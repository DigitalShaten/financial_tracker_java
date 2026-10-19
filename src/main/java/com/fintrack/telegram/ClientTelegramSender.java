package com.fintrack.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void send(long chatId, String text, List<List<BotButton>> keyboard) {
        if (keyboard.isEmpty()) {
            send(chatId, text);
            return;
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (List<BotButton> row : keyboard) {
            InlineKeyboardRow buttons = new InlineKeyboardRow();
            for (BotButton b : row) {
                buttons.add(InlineKeyboardButton.builder().text(b.text()).callbackData(b.data()).build());
            }
            rows.add(buttons);
        }
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text)
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build()).build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить сообщение с кнопками в чат {}", chatId, e);
        }
    }

    @Override
    public void answerCallback(String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (TelegramApiException e) {
            log.warn("Не удалось ответить на нажатие кнопки", e);
        }
    }
}
