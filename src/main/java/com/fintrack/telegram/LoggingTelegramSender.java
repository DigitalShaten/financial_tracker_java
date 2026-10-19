package com.fintrack.telegram;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Бот выключен ({@code telegram.bot.enabled=false}): сообщения пишутся в лог, приложение работает полностью.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "false", matchIfMissing = true)
class LoggingTelegramSender implements TelegramSender {

    @PostConstruct
    void warn() {
        log.warn("Telegram-бот выключен (TELEGRAM_BOT_ENABLED не равен true), сообщения пишутся в лог");
    }

    @Override
    public void send(long chatId, String text) {
        log.info("[Telegram выключен] сообщение в чат {}:\n{}", chatId, text);
    }

    @Override
    public void send(long chatId, String text, List<List<BotButton>> keyboard) {
        String buttons = keyboard.stream().flatMap(List::stream).map(b -> "[" + b.text() + "]")
                .collect(Collectors.joining(" "));
        log.info("[Telegram выключен] сообщение в чат {}:\n{}\n{}", chatId, text, buttons);
    }
}
