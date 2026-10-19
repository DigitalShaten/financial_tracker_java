package com.fintrack.telegram;

import com.fintrack.dto.CategoryView;
import com.fintrack.entity.Category;
import com.fintrack.exception.BusinessException;
import com.fintrack.exception.NotFoundException;
import com.fintrack.service.BotExpenseService;
import com.fintrack.service.MonthlyReportService;
import com.fintrack.service.TelegramLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Команды бота. Непривязанный чат получает только инструкцию - никаких финансовых данных.
 * Запись расхода идёт в три шага: категория (кнопкой) -> сумма -> комментарий.
 */
@Component
@RequiredArgsConstructor
public class BotCommandHandler {

    static final String HELP = """
            Я бот FinTrack: записываю расходы, присылаю отчёт за месяц и сообщаю о превышении бюджета.

            Команды:
            /add — записать расход
            /balance — балансы ваших счетов
            /today — расходы за сегодня и сколько можно потратить
            /cancel — отменить ввод расхода
            /stop — отключить бота от аккаунта

            Чтобы подключить аккаунт, откройте на сайте раздел «Telegram-бот», нажмите «Получить код» \
            и отправьте код сюда (или /start КОД).""";

    static final String NOT_LINKED = "Сначала подключите аккаунт: получите код на сайте в разделе «Telegram-бот» и отправьте его сюда.";
    static final String ASK_AMOUNT = "Введите сумму, например 46,80";
    static final String BAD_AMOUNT = "Не понял сумму. Введите число больше нуля, например 46,80";
    static final String ASK_COMMENT = "Добавьте комментарий (например, «Обед с коллегами») или нажмите «Без комментария».";

    static final String BUTTON_CATEGORY = "cat:";
    static final String BUTTON_SKIP = "skip";
    static final String BUTTON_CANCEL = "cancel";

    private final TelegramLinkService linkService;
    private final MonthlyReportService reportService;
    private final BotExpenseService expenseService;
    private final ExpenseDrafts drafts;

    /** Текстовое сообщение. */
    public BotReply handle(long chatId, String text) {
        String command = text.split("\\s+")[0].toLowerCase();
        String argument = text.contains(" ") ? text.substring(text.indexOf(' ') + 1).trim() : "";
        String compact = text.replace(" ", "");

        // 8 цифр без команды - код привязки, если только мы не ждём сумму или комментарий
        boolean plainCode = compact.matches("\\d{8}") && drafts.find(chatId).isEmpty();
        if (command.equals("/start") || plainCode) {
            drafts.clear(chatId);
            String code = command.equals("/start") ? argument.replace(" ", "") : compact;
            return BotReply.text(code.isEmpty() ? HELP : linkService.link(code, chatId));
        }
        Optional<Long> userId = linkService.findUserIdByChat(chatId);
        if (userId.isEmpty()) {
            return BotReply.text(NOT_LINKED);
        }

        // любая команда прерывает начатый ввод расхода
        if (command.startsWith("/")) {
            drafts.clear(chatId);
            return switch (command) {
                case "/add" -> askCategory(userId.get());
                case "/cancel" -> BotReply.text("Ввод расхода отменён.");
                case "/balance" -> BotReply.text(reportService.balanceText(userId.get()));
                case "/today" -> BotReply.text(reportService.todayText(userId.get()));
                case "/stop" -> BotReply.text(linkService.unlink(userId.get()));
                default -> BotReply.text(HELP);
            };
        }

        Optional<ExpenseDrafts.Draft> draft = drafts.find(chatId);
        if (draft.isEmpty()) {
            return BotReply.text(HELP);
        }
        if (draft.get().waitsForAmount()) {
            BigDecimal amount = parseAmount(text);
            if (amount == null) {
                return BotReply.text(BAD_AMOUNT);
            }
            drafts.setAmount(chatId, draft.get(), amount);
            return new BotReply(ASK_COMMENT, List.of(List.of(new BotButton("Без комментария", BUTTON_SKIP))));
        }
        return save(chatId, userId.get(), draft.get(), text);
    }

    /** Нажатие кнопки под сообщением. */
    public BotReply handleButton(long chatId, String data) {
        Optional<Long> userId = linkService.findUserIdByChat(chatId);
        if (userId.isEmpty()) {
            return BotReply.text(NOT_LINKED);
        }
        if (data == null || data.equals(BUTTON_CANCEL)) {
            drafts.clear(chatId);
            return BotReply.text("Ввод расхода отменён.");
        }
        if (data.equals(BUTTON_SKIP)) {
            Optional<ExpenseDrafts.Draft> draft = drafts.find(chatId);
            if (draft.isEmpty() || draft.get().waitsForAmount()) {
                return BotReply.text("Начните заново: /add");
            }
            return save(chatId, userId.get(), draft.get(), null);
        }
        if (data.startsWith(BUTTON_CATEGORY)) {
            try {
                Long categoryId = Long.valueOf(data.substring(BUTTON_CATEGORY.length()));
                // категория проверяется по пользователю: чужой id из подделанной кнопки не пройдёт
                Category category = expenseService.category(userId.get(), categoryId);
                drafts.start(chatId, category.getId(), category.getName());
                return BotReply.text("Категория «" + category.getName() + "». " + ASK_AMOUNT);
            } catch (NumberFormatException | NotFoundException | BusinessException e) {
                return BotReply.text("Такой категории нет. Начните заново: /add");
            }
        }
        return BotReply.text(HELP);
    }

    private BotReply askCategory(Long userId) {
        List<CategoryView> categories = expenseService.categories(userId);
        if (categories.isEmpty()) {
            return BotReply.text("Нет ни одной категории расходов.");
        }
        List<List<BotButton>> rows = new ArrayList<>();
        for (int i = 0; i < categories.size(); i += 2) {
            List<BotButton> row = new ArrayList<>();
            for (CategoryView c : categories.subList(i, Math.min(i + 2, categories.size()))) {
                row.add(new BotButton(c.name(), BUTTON_CATEGORY + c.id()));
            }
            rows.add(row);
        }
        rows.add(List.of(new BotButton("Отмена", BUTTON_CANCEL)));
        return new BotReply("Выберите категорию расхода:", rows);
    }

    private BotReply save(long chatId, Long userId, ExpenseDrafts.Draft draft, String comment) {
        drafts.clear(chatId);
        try {
            return BotReply.text(expenseService.addExpense(userId, draft.categoryId(), draft.amount(), comment));
        } catch (BusinessException | NotFoundException e) {
            return BotReply.text("Не удалось записать расход: " + e.getMessage());
        }
    }

    /** "46,80", "46.80", "1 286,40" -> число; всё остальное (и ноль, и минус) -> null. */
    static BigDecimal parseAmount(String text) {
        String normalized = text.replace(" ", "").replace("\u00a0", "").replace(',', '.');
        if (!normalized.matches("\\d{1,15}(\\.\\d{1,2})?")) {
            return null;
        }
        BigDecimal amount = new BigDecimal(normalized);
        return amount.signum() > 0 ? amount : null;
    }
}
