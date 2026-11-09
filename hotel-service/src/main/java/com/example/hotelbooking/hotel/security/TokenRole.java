package com.example.hotelbooking.hotel.security;

import java.util.Locale;
import java.util.Optional;

public enum TokenRole {

    ADMIN,
    USER,
    SERVICE;

    public static Optional<TokenRole> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
