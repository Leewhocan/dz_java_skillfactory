package com.example.hotelbooking.hotel.dto;

public record RoomRequest(
        Long hotelId,
        String number,
        Boolean available
) {
}
