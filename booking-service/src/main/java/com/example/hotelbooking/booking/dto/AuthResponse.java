package com.example.hotelbooking.booking.dto;

public record AuthResponse(
        String token,
        String type,
        long expiresInSeconds,
        String username,
        String role
) {
}
