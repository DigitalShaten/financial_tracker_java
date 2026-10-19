package com.fintrack.telegram;

/**
 * Кнопка под сообщением бота: подпись и данные, которые придут обратно при нажатии.
 */
public record BotButton(String text, String data) {
}
