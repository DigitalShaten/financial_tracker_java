package com.fintrack.service;

import com.fintrack.dto.BudgetRow;
import com.fintrack.dto.CategoryTotal;
import com.fintrack.entity.Account;
import com.fintrack.entity.NotificationSettings;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.exception.BusinessException;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.NotificationSettingsRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.service.calc.DailyAllowanceCalculator;
import com.fintrack.telegram.MonthlyReport;
import com.fintrack.telegram.ReportTextGenerator;
import com.fintrack.telegram.TelegramSender;
import com.fintrack.util.DateFormat;
import com.fintrack.util.MoneyFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Числа ежемесячного отчёта, отправка отчётов и тексты команд бота /balance и /today.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportService {

    private final AccessService accessService;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final TransactionService transactionService;
    private final UserService userService;
    private final TelegramLinkService linkService;
    private final NotificationSettingsRepository settingsRepository;
    private final ReportTextGenerator textGenerator;
    private final TelegramSender sender;

    /** Отчёт за месяц: личный (свои счета) или семейный (режим "Вместе"). */
    @Transactional(readOnly = true)
    public MonthlyReport buildReport(Long userId, YearMonth month, boolean family) {
        User user = userService.get(userId);
        ViewMode mode = family ? ViewMode.all() : ViewMode.me();
        Set<Long> accounts = accessService.visibleAccountIds(userId, mode);
        List<Long> users = accessService.visibleUserIds(userId, mode);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        BigDecimal income = transactionRepository.sumByTypeOrZero(accounts, TransactionType.INCOME, from, to);
        BigDecimal expense = transactionRepository.sumByTypeOrZero(accounts, TransactionType.EXPENSE, from, to);
        YearMonth previous = month.minusMonths(1);
        BigDecimal previousExpense = transactionRepository.sumByTypeOrZero(accounts, TransactionType.EXPENSE,
                previous.atDay(1), previous.atEndOfMonth());

        List<BudgetRow> budgets = budgetService.rows(users, accounts, month, user.getCurrency());
        List<MonthlyReport.OverBudget> over = budgets.stream().filter(BudgetRow::over)
                .map(b -> new MonthlyReport.OverBudget(b.categoryName(), overPercent(b.note())))
                .toList();
        List<MonthlyReport.GoalLine> goals = goalService.reportGoals(userId, family, month).stream()
                .map(g -> new MonthlyReport.GoalLine(g.name(), g.percent(), g.onTrack())).toList();

        String title = (family ? "Отчёт семьи за " : "Отчёт за ") + DateFormat.monthLower(month);
        return new MonthlyReport(title, user.getCurrency(), income, expense, previousExpense,
                DateFormat.monthPrepositional(previous.getMonth()), goalService.savedInMonth(userId, family, month),
                topCategories(accounts, from, to), budgets.size(), over, goals);
    }

    @Transactional(readOnly = true)
    public String reportText(Long userId, YearMonth month, boolean family) {
        return textGenerator.generate(buildReport(userId, month, family));
    }

    /**
     * Ежемесячная отправка: личный отчёт, затем семейный (если включён и в семье есть другие участники).
     * Записывает месяц последней отправки, чтобы не отправить повторно.
     */
    @Transactional
    public void sendMonthlyReport(Long userId, YearMonth month) {
        Long chatId = linkService.chatOf(userId).orElse(null);
        if (chatId == null) {
            return;
        }
        NotificationSettings settings = settingsRepository.findById(userId).orElseThrow();
        sender.send(chatId, reportText(userId, month, false));
        if (settings.isFamilyReport() && accessService.hasOtherMembers(userId)) {
            sender.send(chatId, reportText(userId, month, true));
        }
        settings.setLastReportMonth(month.atDay(1));
        log.info("Отчёт за {} отправлен пользователю {}", month, userId);
    }

    /** Тестовый отчёт за прошлый месяц - по кнопке на странице (месяц последней отправки не меняется). */
    @Transactional(readOnly = true)
    public void sendTestReport(Long userId) {
        Long chatId = linkService.chatOf(userId)
                .orElseThrow(() -> new BusinessException("Сначала подключите Telegram-бота"));
        sender.send(chatId, reportText(userId, previousMonth(userId), false));
    }

    /** Предпросмотр на странице "Telegram-бот": настоящий отчёт за прошлый месяц. */
    @Transactional(readOnly = true)
    public String previewText(Long userId) {
        return reportText(userId, previousMonth(userId), false);
    }

    /** Команда /balance: балансы своих неархивных счетов и итог. */
    @Transactional(readOnly = true)
    public String balanceText(Long userId) {
        User user = userService.get(userId);
        List<Account> accounts = accountRepository.findAllByUser_IdAndArchivedFalseOrderByName(userId);
        if (accounts.isEmpty()) {
            return "Счетов пока нет. Добавьте их на сайте в разделе «Счета и бюджеты».";
        }
        StringBuilder text = new StringBuilder("Балансы счетов:");
        BigDecimal total = BigDecimal.ZERO;
        for (Account a : accounts) {
            text.append('\n').append(a.getName()).append(" — ").append(MoneyFormat.format(a.getBalance(), user.getCurrency()));
            total = total.add(a.getBalance());
        }
        text.append("\n\nИтого: ").append(MoneyFormat.format(total, user.getCurrency()));
        return text.toString();
    }

    /** Команда /today: расходы за сегодня и "Можно потратить сегодня". */
    @Transactional(readOnly = true)
    public String todayText(Long userId) {
        User user = userService.get(userId);
        LocalDate today = transactionService.today(userId);
        YearMonth month = YearMonth.from(today);
        Set<Long> accounts = accessService.visibleAccountIds(userId, ViewMode.me());
        BigDecimal spentToday = transactionRepository.sumByTypeOrZero(accounts, TransactionType.EXPENSE, today, today);
        BigDecimal income = transactionRepository.sumByTypeOrZero(accounts, TransactionType.INCOME,
                month.atDay(1), month.atEndOfMonth());
        BigDecimal expense = transactionRepository.sumByTypeOrZero(accounts, TransactionType.EXPENSE,
                month.atDay(1), month.atEndOfMonth());
        BigDecimal allowance = DailyAllowanceCalculator.calculate(income,
                goalService.plannedMonthlySavings(userId, ViewMode.me()), expense, today);
        return "Сегодня, " + DateFormat.dayMonth(today) + "\n"
                + "Потрачено: " + MoneyFormat.format(spentToday, user.getCurrency()) + "\n"
                + "Можно потратить сегодня: " + MoneyFormat.format(allowance, user.getCurrency());
    }

    /** Прошлый месяц по часовому поясу пользователя - за него показывается и отправляется отчёт. */
    public YearMonth previousMonth(Long userId) {
        return YearMonth.from(transactionService.today(userId)).minusMonths(1);
    }

    /** Топ-3 категории расходов (категории с одинаковым названием у разных участников складываются). */
    private List<CategoryTotal> topCategories(Set<Long> accounts, LocalDate from, LocalDate to) {
        if (accounts.isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> byName = new LinkedHashMap<>();
        for (CategoryTotal t : transactionRepository.expensesByCategory(accounts, from, to)) {
            byName.merge(t.name(), t.total(), BigDecimal::add);
        }
        List<CategoryTotal> sorted = new ArrayList<>();
        byName.forEach((name, total) -> sorted.add(new CategoryTotal(null, name, total)));
        sorted.sort((a, b) -> b.total().compareTo(a.total()));
        return sorted.stream().limit(3).toList();
    }

    /** "Превышен на 18%" -> 18. */
    private static int overPercent(String note) {
        return Integer.parseInt(note.replaceAll("\\D", ""));
    }
}
