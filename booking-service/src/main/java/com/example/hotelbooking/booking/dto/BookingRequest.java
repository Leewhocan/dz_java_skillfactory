package com.example.hotelbooking.booking.dto;

import java.time.LocalDate;

public record BookingRequest(
        Long roomId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean autoSelect,
        String requestId
) {
}
