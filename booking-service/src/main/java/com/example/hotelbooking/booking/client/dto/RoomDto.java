package com.example.hotelbooking.booking.client.dto;

public record RoomDto(
        Long id,
        Long hotelId,
        String hotelName,
        String number,
        boolean available,
        int timesBooked
) {
}
