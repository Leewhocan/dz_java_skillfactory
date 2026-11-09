package com.example.hotelbooking.booking.dto;

import com.example.hotelbooking.booking.domain.BookingStatus;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        Long userId,
        String username,
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        BookingStatus status,
        Instant createdAt
) {
}
