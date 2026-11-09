package com.example.hotelbooking.hotel.dto;

import java.time.LocalDate;

public record RoomAvailabilityResponse(
        Long roomId,
        boolean available,
        int timesBooked,
        LocalDate startDate,
        LocalDate endDate
) {
}
