package com.fintrack.dto;

public record TelegramStatus(boolean linked, String linkedText, String code, String codeExpiresText) {
}
