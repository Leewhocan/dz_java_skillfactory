package com.example.hotelbooking.booking.service;

import com.example.hotelbooking.booking.client.HotelClient;
import com.example.hotelbooking.booking.client.dto.RoomAvailabilityDto;
import com.example.hotelbooking.booking.client.dto.RoomBlockDto;
import com.example.hotelbooking.booking.client.dto.RoomDto;
import com.example.hotelbooking.booking.domain.Booking;
import com.example.hotelbooking.booking.domain.BookingStatus;
import com.example.hotelbooking.booking.domain.User;
import com.example.hotelbooking.booking.domain.UserRole;
import com.example.hotelbooking.booking.dto.BookingRequest;
import com.example.hotelbooking.booking.dto.BookingResponse;
import com.example.hotelbooking.booking.exception.BadRequestException;
import com.example.hotelbooking.booking.exception.ConflictException;
import com.example.hotelbooking.booking.exception.ExternalServiceException;
import com.example.hotelbooking.booking.exception.ResourceNotFoundException;
import com.example.hotelbooking.booking.mapper.BookingMapper;
import com.example.hotelbooking.booking.repository.BookingRepository;
import com.example.hotelbooking.booking.repository.UserRepository;
import com.example.hotelbooking.booking.security.UserPrincipal;
import feign.FeignException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final HotelClient hotelClient;
    private final BookingMapper bookingMapper;

    public BookingResponse createBooking(UserPrincipal principal, BookingRequest request, String requestId) {
        validateDates(request);

        String idempotencyKey = resolveRequestId(request, requestId);
        Optional<Booking> alreadyProcessed = findProcessedBooking(idempotencyKey, principal);
        if (alreadyProcessed.isPresent()) {
            log.info("Idempotent replay for requestId={} - returning booking {}", idempotencyKey,
                    alreadyProcessed.get().getId());
            return bookingMapper.toResponse(alreadyProcessed.get());
        }

        Long roomId = resolveRoomId(request);
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + principal.getId() + " not found"));

        Booking booking;
        try {
            booking = bookingRepository.saveAndFlush(Booking.builder()
                    .user(user)
                    .roomId(roomId)
                    .startDate(request.startDate())
                    .endDate(request.endDate())
                    .status(BookingStatus.PENDING)
                    .requestId(idempotencyKey)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate for requestId={}, falling back to the stored booking", idempotencyKey);
            return findProcessedBooking(idempotencyKey, principal)
                    .map(bookingMapper::toResponse)
                    .orElseThrow(() -> new ConflictException("Could not create booking: duplicate request"));
        }

        log.info("Booking {} created with status PENDING (roomId={}, requestId={}, user={})",
                booking.getId(), roomId, idempotencyKey, user.getUsername());

        return bookingMapper.toResponse(runConfirmationStep(booking));
    }
    private Booking runConfirmationStep(Booking booking) {
        RoomBlockDto period = new RoomBlockDto(booking.getStartDate(), booking.getEndDate());
        try {
            RoomAvailabilityDto confirmation = hotelClient.confirmAvailability(booking.getRoomId(), period);
            if (confirmation == null || !confirmation.available()) {
                log.warn("hotel-service did not confirm room {} for booking {}",
                        booking.getRoomId(), booking.getId());
                cancelWithCompensation(booking, true, false);
                throw new ConflictException("Room " + booking.getRoomId()
                        + " is not available for the requested dates");
            }
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
            log.info("Booking {} CONFIRMED (roomId={}, {} .. {}, timesBooked={})",
                    booking.getId(), booking.getRoomId(), booking.getStartDate(), booking.getEndDate(),
                    confirmation.timesBooked());
            return booking;
        } catch (FeignException ex) {
            log.error("Confirmation of booking {} failed (roomId={}, status={}): {}",
                    booking.getId(), booking.getRoomId(), ex.status(), ex.getMessage());
            boolean conflict = ex.status() == HttpStatus.CONFLICT.value();
            cancelWithCompensation(booking, true, !conflict);
            if (conflict) {
                throw new ConflictException("Room " + booking.getRoomId()
                        + " is already booked for the requested dates");
            }
            throw new ExternalServiceException("Could not confirm the booking with hotel-service", ex);
        } catch (ConflictException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Confirmation of booking {} failed (roomId={}): {}",
                    booking.getId(), booking.getRoomId(), ex.toString());
            cancelWithCompensation(booking, true, true);
            throw new ExternalServiceException("Could not confirm the booking with hotel-service", ex);
        }
    }

    private Booking cancelWithCompensation(Booking booking, boolean dropIdempotencyKey, boolean releaseRoom) {
        if (dropIdempotencyKey) {
            booking.setRequestId(null);
        }
        booking.setStatus(BookingStatus.CANCELLED);
        Booking cancelled = bookingRepository.save(booking);
        if (!releaseRoom) {
            log.info("Booking {} cancelled without compensation (no block was created)", booking.getId());
            return cancelled;
        }
        try {
            hotelClient.releaseRoom(booking.getRoomId(),
                    new RoomBlockDto(booking.getStartDate(), booking.getEndDate()));
            log.info("Compensation completed: room {} released for cancelled booking {}",
                    booking.getRoomId(), booking.getId());
        } catch (RuntimeException ex) {
            log.error("Compensation FAILED for booking {} (roomId={}): {}",
                    booking.getId(), booking.getRoomId(), ex.toString());
        }
        return cancelled;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findBookingsForUser(UserPrincipal principal) {
        return bookingMapper.toResponse(bookingRepository.findByUserIdOrderByCreatedAtDesc(principal.getId()));
    }

    @Transactional(readOnly = true)
    public BookingResponse findBookingForUser(Long id, UserPrincipal principal) {
        return bookingMapper.toResponse(loadBookingForUser(id, principal));
    }

    public void cancelBooking(Long id, UserPrincipal principal) {
        Booking booking = loadBookingForUser(id, principal);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info("Booking {} is already cancelled - nothing to do", id);
            return;
        }

        BookingStatus previousStatus = booking.getStatus();
        if (previousStatus == BookingStatus.CONFIRMED) {
            cancelWithCompensation(booking, false, true);
        } else {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);
            log.info("Booking {} cancelled (previous status {})", id, previousStatus);
        }
    }

    private Booking loadBookingForUser(Long id, UserPrincipal principal) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking with id " + id + " not found"));

        boolean isAdmin = principal.getRole() == UserRole.ADMIN;
        if (!isAdmin && !booking.getUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Booking " + id + " does not belong to the current user");
        }
        return booking;
    }
    private Optional<Booking> findProcessedBooking(String idempotencyKey, UserPrincipal principal) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return bookingRepository.findByRequestId(idempotencyKey).map(booking -> {
            if (!booking.getUser().getId().equals(principal.getId())) {
                throw new ConflictException(
                        "requestId '" + idempotencyKey + "' has already been used by another user");
            }
            return booking;
        });
    }

    private Long resolveRoomId(BookingRequest request) {
        if (Boolean.TRUE.equals(request.autoSelect())) {
            return selectBestRoom();
        }
        if (request.roomId() == null) {
            throw new BadRequestException("roomId is required when autoSelect is not enabled");
        }
        return request.roomId();
    }

    private Long selectBestRoom() {
        List<RoomDto> recommended;
        try {
            recommended = hotelClient.recommendRooms();
        } catch (RuntimeException ex) {
            log.error("Failed to fetch room recommendations from hotel-service: {}", ex.toString());
            throw new ExternalServiceException("Could not fetch room recommendations from hotel-service", ex);
        }

        if (recommended == null || recommended.isEmpty()) {
            throw new ConflictException("No available rooms to auto-select");
        }

        RoomDto best = recommended.get(0);
        log.info("Auto-selected room {} (hotel={}, timesBooked={})", best.id(), best.hotelName(), best.timesBooked());
        return best.id();
    }

    private static String resolveRequestId(BookingRequest request, String idempotencyKeyHeader) {
        String value = request.requestId() != null && !request.requestId().isBlank()
                ? request.requestId()
                : idempotencyKeyHeader;
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateDates(BookingRequest request) {
        if (request.startDate() == null || request.endDate() == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BadRequestException("endDate must be after startDate");
        }
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("startDate must not be in the past");
        }
    }
}
