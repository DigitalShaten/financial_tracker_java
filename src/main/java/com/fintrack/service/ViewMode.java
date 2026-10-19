package com.fintrack.service;

import com.fintrack.exception.NotFoundException;

/**
 * Чьи данные показывать: "me" - только свои, "all" - свои и открытые счета семьи,
 * число - открытые счета одного участника семьи.
 */
public record ViewMode(String kind, Long memberId) {

    public static ViewMode me() {
        return new ViewMode("me", null);
    }

    public static ViewMode all() {
        return new ViewMode("all", null);
    }

    public static ViewMode parse(String value) {
        if (value == null || value.isBlank() || value.equals("me")) {
            return me();
        }
        if (value.equals("all")) {
            return all();
        }
        try {
            return new ViewMode("member", Long.valueOf(value));
        } catch (NumberFormatException e) {
            throw new NotFoundException("Участник не найден");
        }
    }

    public boolean isOnlyMe() {
        return kind.equals("me");
    }

    public boolean isAll() {
        return kind.equals("all");
    }

    public boolean includesMe() {
        return !kind.equals("member");
    }

    /** Значение для параметра ?view= в ссылках. */
    public String value() {
        return kind.equals("member") ? String.valueOf(memberId) : kind;
    }
}
