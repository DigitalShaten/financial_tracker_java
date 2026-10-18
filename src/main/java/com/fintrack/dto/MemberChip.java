package com.fintrack.dto;

/**
 * Аватар участника семьи в боковом меню: первая буква имени; текущий пользователь — зелёный.
 */
public record MemberChip(String initial, String name, boolean me) {
}
