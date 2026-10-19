package com.fintrack.telegram;

/**
 * Отправка сообщений в Telegram. Две реализации: настоящий клиент (бот включён)
 * и запись в лог (бот выключен) - остальной код не знает, какая из них работает.
 */
public interface TelegramSender {

    void send(long chatId, String text);
}
