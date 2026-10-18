package com.fintrack.dto;

/** Действующий код приглашения: код, до какого времени действует и ссылка. */
public record InviteView(String code, String expiresText, String link) {
}
