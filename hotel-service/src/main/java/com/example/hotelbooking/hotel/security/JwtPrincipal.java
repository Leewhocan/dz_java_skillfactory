package com.example.hotelbooking.hotel.security;

public record JwtPrincipal(
        Long userId,
        String username,
        TokenRole role
) {
}
