package com.example.hotelbooking.booking.controller;

import com.example.hotelbooking.booking.dto.BookingRequest;
import com.example.hotelbooking.booking.dto.BookingResponse;
import com.example.hotelbooking.booking.security.UserPrincipal;
import com.example.hotelbooking.booking.service.BookingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/booking")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                  @RequestBody BookingRequest request) {
        return bookingService.createBooking(principal, request, idempotencyKey);
    }

    @GetMapping("/bookings")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.findBookingsForUser(principal);
    }

    @GetMapping("/booking/{id}")
    public BookingResponse byId(@PathVariable("id") Long id,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.findBookingForUser(id, principal);
    }

    @DeleteMapping("/booking/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable("id") Long id,
                       @AuthenticationPrincipal UserPrincipal principal) {
        bookingService.cancelBooking(id, principal);
    }
}
