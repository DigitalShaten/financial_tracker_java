package com.fintrack.dto;

public record AdminUserView(Long id, String name, String email, String registeredText, boolean enabled) {
}
