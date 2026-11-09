package com.example.hotelbooking.hotel.service;

import com.example.hotelbooking.hotel.domain.Hotel;
import com.example.hotelbooking.hotel.domain.Room;
import com.example.hotelbooking.hotel.domain.RoomBlock;
import com.example.hotelbooking.hotel.dto.RoomAvailabilityResponse;
import com.example.hotelbooking.hotel.dto.RoomBlockRequest;
import com.example.hotelbooking.hotel.dto.RoomRequest;
import com.example.hotelbooking.hotel.dto.RoomResponse;
import com.example.hotelbooking.hotel.exception.BadRequestException;
import com.example.hotelbooking.hotel.exception.ResourceNotFoundException;
import com.example.hotelbooking.hotel.exception.RoomNotAvailableException;
import com.example.hotelbooking.hotel.mapper.RoomMapper;
import com.example.hotelbooking.hotel.repository.HotelRepository;
import com.example.hotelbooking.hotel.repository.RoomBlockRepository;
import com.example.hotelbooking.hotel.repository.RoomRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomBlockRepository roomBlockRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Transactional
    public RoomResponse create(RoomRequest request) {
        if (request.hotelId() == null) {
            throw new BadRequestException("hotelId must not be null");
        }
        if (request.number() == null || request.number().isBlank()) {
            throw new BadRequestException("Room number must not be blank");
        }

        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Hotel with id " + request.hotelId() + " not found"));

        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        room.setAvailable(request.available() == null || request.available());

        return roomMapper.toResponse(roomRepository.save(room));
    }

    public List<RoomResponse> findAllAvailable() {
        return roomMapper.toResponse(roomRepository.findByAvailableTrue());
    }

    public List<RoomResponse> recommend() {
        return roomMapper.toResponse(roomRepository.findByAvailableTrueOrderByTimesBookedAscIdAsc());
    }

    @Transactional
    public RoomAvailabilityResponse confirmAvailability(Long roomId, RoomBlockRequest request) {
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        validatePeriod(startDate, endDate);

        Room room = lockRoom(roomId);
        if (!room.isAvailable()) {
            throw new RoomNotAvailableException("Room " + roomId + " is not available for booking");
        }

        List<RoomBlock> overlaps = roomBlockRepository.findOverlappingForUpdate(roomId, startDate, endDate);
        if (!overlaps.isEmpty()) {
            RoomBlock conflict = overlaps.get(0);
            throw new RoomNotAvailableException(("Room %d is already booked from %s to %s "
                    + "(requested %s .. %s)").formatted(
                    roomId, conflict.getStartDate(), conflict.getEndDate(), startDate, endDate));
        }

        roomBlockRepository.save(RoomBlock.builder()
                .roomId(roomId)
                .startDate(startDate)
                .endDate(endDate)
                .build());

        room.setTimesBooked(room.getTimesBooked() + 1);
        log.info("Blocked room id={} for {} .. {} (timesBooked={})",
                roomId, startDate, endDate, room.getTimesBooked());

        return new RoomAvailabilityResponse(room.getId(), room.isAvailable(), room.getTimesBooked(),
                startDate, endDate);
    }

    @Transactional
    public void release(Long roomId, RoomBlockRequest request) {
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        validatePeriod(startDate, endDate);

        Room room = lockRoom(roomId);
        List<RoomBlock> blocks = roomBlockRepository.findOverlappingForUpdate(roomId, startDate, endDate);
        if (blocks.isEmpty()) {
            log.info("Release for room id={} ({} .. {}) found no block - nothing to do",
                    roomId, startDate, endDate);
            return;
        }

        roomBlockRepository.deleteAll(blocks);
        room.setTimesBooked(Math.max(0, room.getTimesBooked() - blocks.size()));
        log.info("Released {} block(s) of room id={} for {} .. {} (timesBooked={})",
                blocks.size(), roomId, startDate, endDate, room.getTimesBooked());
    }

    private Room lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room with id " + roomId + " not found"));
    }

    private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new BadRequestException("endDate must be after startDate");
        }
        if (startDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("startDate must not be in the past");
        }
    }
}
