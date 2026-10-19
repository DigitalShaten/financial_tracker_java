package com.fintrack.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Бот в режиме long polling: получает сообщения и нажатия кнопок, отдаёт их в {@link BotCommandHandler},
 * отправляет ответ.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.enabled", havingValue = "true")
public class FinTrackBot implements LongPollingSingleThreadUpdateConsumer {

    private final BotCommandHandler commandHandler;
    private final TelegramSender sender;

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            sender.answerCallback(callback.getId());
            // бот работает в личных чатах, там id чата совпадает с id пользователя
            long chatId = callback.getFrom().getId();
            reply(chatId, commandHandler.handleButton(chatId, callback.getData()));
            return;
        }
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        long chatId = update.getMessage().getChatId();
        reply(chatId, commandHandler.handle(chatId, update.getMessage().getText().trim()));
    }

    private void reply(long chatId, BotReply reply) {
        sender.send(chatId, reply.text(), reply.keyboard());
    }
}
