package com.example.hotelbooking.booking.client.dto;

import java.time.LocalDate;

public record RoomAvailabilityDto(
        Long roomId,
        boolean available,
        int timesBooked,
        LocalDate startDate,
        LocalDate endDate
) {
}
