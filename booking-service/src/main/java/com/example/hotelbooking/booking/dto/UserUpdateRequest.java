package com.example.hotelbooking.booking.dto;

import com.example.hotelbooking.booking.domain.UserRole;

public record UserUpdateRequest(
        Long id,
        String username,
        String password,
        UserRole role
) {
}
