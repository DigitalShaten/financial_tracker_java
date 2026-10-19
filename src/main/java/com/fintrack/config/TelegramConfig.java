package com.fintrack.config;

import com.fintrack.telegram.FinTrackBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Настоящий бот - только при {@code telegram.bot.enabled=true} и заданном токене.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class TelegramConfig {

    @Bean
    TelegramClient telegramClient(@Value("${telegram.bot.token}") String token) {
        return new OkHttpTelegramClient(token);
    }

    @Bean(destroyMethod = "close")
    TelegramBotsLongPollingApplication botsApplication(FinTrackBot bot,
                                                       @Value("${telegram.bot.token}") String token,
                                                       @Value("${telegram.bot.username}") String username)
            throws TelegramApiException {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_ENABLED=true, но TELEGRAM_BOT_TOKEN не задан");
        }
        TelegramBotsLongPollingApplication application = new TelegramBotsLongPollingApplication();
        application.registerBot(token, bot);
        log.info("Telegram-бот запущен: @{}", username);
        return application;
    }
}
