package com.fintrack.telegram;

import java.util.List;

/**
 * Ответ бота: текст и, если нужно, кнопки (ряды кнопок).
 */
public record BotReply(String text, List<List<BotButton>> keyboard) {

    public static BotReply text(String text) {
        return new BotReply(text, List.of());
    }

    public boolean hasKeyboard() {
        return !keyboard.isEmpty();
    }
}
