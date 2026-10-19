package com.fintrack.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Бот включён: отправка через Telegram Bot API. Ошибка отправки не роняет вызывающий код.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
class ClientTelegramSender implements TelegramSender {

    private final TelegramClient telegramClient;

    @Override
    public void send(long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить сообщение в чат {}", chatId, e);
        }
    }
}
