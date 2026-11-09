package com.example.hotelbooking.booking.dto;

import com.example.hotelbooking.booking.domain.UserRole;

public record UserResponse(
        Long id,
        String username,
        UserRole role
) {
}
