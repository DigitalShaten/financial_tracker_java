package com.fintrack.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Бот в режиме long polling: получает сообщение, отдаёт текст в {@link BotCommandHandler}, отправляет ответ.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class FinTrackBot implements LongPollingSingleThreadUpdateConsumer {

    private final BotCommandHandler commandHandler;
    private final TelegramSender sender;

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        long chatId = update.getMessage().getChatId();
        String reply = commandHandler.handle(chatId, update.getMessage().getText().trim());
        sender.send(chatId, reply);
    }
}
