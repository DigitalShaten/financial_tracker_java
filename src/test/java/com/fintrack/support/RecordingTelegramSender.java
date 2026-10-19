package com.fintrack.support;

import com.fintrack.telegram.TelegramSender;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@TestComponent
@Primary
public class RecordingTelegramSender implements TelegramSender {

    /** Отправленное сообщение: чат и текст. */
    public record Message(long chatId, String text) {
    }

    private final List<Message> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(long chatId, String text) {
        messages.add(new Message(chatId, text));
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }
}
