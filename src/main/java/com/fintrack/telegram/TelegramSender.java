package com.fintrack.telegram;

import java.util.List;

/**
 * Отправка сообщений в Telegram. Две реализации: настоящий клиент (бот включён)
 * и запись в лог (бот выключен) - остальной код не знает, какая из них работает.
 */
public interface TelegramSender {

    void send(long chatId, String text);

    /** Сообщение с кнопками. По умолчанию кнопки не показываются. */
    default void send(long chatId, String text, List<List<BotButton>> keyboard) {
        send(chatId, text);
    }

    /** Убрать "часики" на нажатой кнопке. */
    default void answerCallback(String callbackQueryId) {
    }
}
