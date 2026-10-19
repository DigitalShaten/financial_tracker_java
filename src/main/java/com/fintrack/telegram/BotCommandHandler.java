package com.fintrack.telegram;

import com.fintrack.service.MonthlyReportService;
import com.fintrack.service.TelegramLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Команды бота. Непривязанный чат получает только инструкцию - никаких финансовых данных.
 */
@Component
@RequiredArgsConstructor
public class BotCommandHandler {

    static final String HELP = """
            Я бот FinTrack: присылаю отчёт за месяц и сообщаю о превышении бюджета.

            Команды:
            /balance — балансы ваших счетов
            /today — расходы за сегодня и сколько можно потратить
            /stop — отключить бота от аккаунта

            Чтобы подключить аккаунт, откройте на сайте раздел «Telegram-бот», нажмите «Получить код» \
            и отправьте код сюда (или /start КОД).""";

    static final String NOT_LINKED = "Сначала подключите аккаунт: получите код на сайте в разделе «Telegram-бот» и отправьте его сюда.";

    private final TelegramLinkService linkService;
    private final MonthlyReportService reportService;

    public String handle(long chatId, String text) {
        String command = text.split("\\s+")[0].toLowerCase();
        String argument = text.contains(" ") ? text.substring(text.indexOf(' ') + 1).trim() : "";
        String compact = text.replace(" ", "");

        Optional<Long> userId = linkService.findUserIdByChat(chatId);

        if (command.equals("/start") || compact.matches("\\d{8}")) {
            String code = command.equals("/start") ? argument.replace(" ", "") : compact;
            return code.isEmpty() ? HELP : linkService.link(code, chatId);
        }
        if (userId.isEmpty()) {
            return NOT_LINKED;
        }
        return switch (command) {
            case "/balance" -> reportService.balanceText(userId.get());
            case "/today" -> reportService.todayText(userId.get());
            case "/stop" -> linkService.unlink(userId.get());
            default -> HELP;
        };
    }
}
