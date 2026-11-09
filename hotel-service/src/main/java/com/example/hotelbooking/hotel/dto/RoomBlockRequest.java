package com.example.hotelbooking.hotel.dto;

import java.time.LocalDate;

public record RoomBlockRequest(
        LocalDate startDate,
        LocalDate endDate
) {
}
