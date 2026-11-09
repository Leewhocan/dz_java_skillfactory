package com.example.hotelbooking.hotel.dto;

public record RoomResponse(
        Long id,
        Long hotelId,
        String hotelName,
        String number,
        boolean available,
        int timesBooked
) {
}
