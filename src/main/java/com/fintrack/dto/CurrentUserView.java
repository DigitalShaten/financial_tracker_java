package com.fintrack.dto;

public record CurrentUserView(
        Long id,
        String name,
        String currency,
        String familyName,
        boolean admin
) {}