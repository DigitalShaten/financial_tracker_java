package com.fintrack.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Бот выключен ({@code telegram.bot.enabled=false}): сообщения пишутся в лог, приложение работает полностью.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "false", matchIfMissing = true)
class LoggingTelegramSender implements TelegramSender {

    @Override
    public void send(long chatId, String text) {
        log.info("[Telegram выключен] сообщение в чат {}:\n{}", chatId, text);
    }
}
