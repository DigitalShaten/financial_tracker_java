package com.fintrack.service;

import com.fintrack.dto.*;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.repository.AccountRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.service.calc.DailyAllowanceCalculator;
import com.fintrack.util.DateFormat;
import com.fintrack.util.MoneyFormat;
import com.fintrack.util.Plurals;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Собирает страницу «Обзор» для выбранного режима («Я», участник, «Вместе») и месяца.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int CHART_MONTHS = 6;
    private static final int CHART_HEIGHT_PX = 150;
    private static final int RECENT_LIMIT = 6;

    private final AccessService accessService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final BudgetService budgetService;
    private final GoalService goalService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public DashboardView build(Long userId, ViewMode mode, YearMonth month) {
        User user = userService.get(userId);
        String currency = user.getCurrency();
        LocalDate today = transactionService.today(userId);
        YearMonth current = YearMonth.from(today);

        Set<Long> visible = accessService.visibleAccountIds(userId, mode);
        List<Long> visibleUsers = accessService.visibleUserIds(userId, mode);
        List<MemberOption> members = accessService.familyMembers(userId);

        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        BigDecimal income = transactionRepository.sumByTypeOrZero(visible, TransactionType.INCOME, from, to);
        BigDecimal expense = transactionRepository.sumByTypeOrZero(visible, TransactionType.EXPENSE, from, to);
        BigDecimal balance = visible.isEmpty() ? BigDecimal.ZERO
                : Optional.ofNullable(accountRepository.sumBalance(visible)).orElse(BigDecimal.ZERO);

        String share = income.signum() > 0
                ? expense.multiply(BigDecimal.valueOf(100)).divide(income, 0, RoundingMode.HALF_UP) + "% от доходов"
                : "доходов за месяц нет";

        String todayText;
        String todayHint;
        if (month.equals(current)) {
            BigDecimal planned = goalService.plannedMonthlySavings(userId, mode);
            todayText = MoneyFormat.format(DailyAllowanceCalculator.calculate(income, planned, expense, today));
            int days = DailyAllowanceCalculator.daysLeft(today);
            todayHint = "Чтобы уложиться в бюджеты и отложить на цели. До конца месяца "
                    + Plurals.count(days, "день", "дня", "дней");
        } else {
            todayText = null;
            todayHint = "Месяц завершён";
        }

        List<BudgetRow> budgets = budgetService.rows(visibleUsers, visible, month, currency);
        List<GoalMini> goals = goalService.miniGoals(userId, mode, currency);
        List<OperationView> recent = transactionService.recent(userId, visible, to, RECENT_LIMIT);

        String legendOthers = othersLegend(mode, members);

        return new DashboardView(user.getName(), DateFormat.monthTitle(month), month.toString(),
                month.minusMonths(1).toString(), month.isBefore(current) ? month.plusMonths(1).toString() : null,
                mode.value(), viewOptions(mode, members), viewNote(mode, members),
                MoneyFormat.format(balance), Plurals.count(visible.size(), "счёт", "счёта", "счетов"),
                MoneyFormat.format(income), MoneyFormat.format(expense), share, todayText, todayHint,
                budgets, goals, recent, mode.isAll() && members.size() > 1,
                chart(userId, visible, month), mode.includesMe() ? user.getName() : null, legendOthers);
    }

    /** Переключатель показывается, только если в семье есть другие участники. */
    private static List<ViewOption> viewOptions(ViewMode mode, List<MemberOption> members) {
        if (members.size() < 2) {
            return List.of();
        }
        List<ViewOption> options = new ArrayList<>();
        options.add(new ViewOption("Я", "me", mode.isOnlyMe()));
        members.stream().filter(m -> !m.me()).forEach(m -> options.add(
                new ViewOption(m.name(), String.valueOf(m.id()), m.id().equals(mode.memberId()))));
        options.add(new ViewOption("Вместе", "all", mode.isAll()));
        return options;
    }

    private static String viewNote(ViewMode mode, List<MemberOption> members) {
        if (members.size() < 2) {
            return null;
        }
        if (mode.isOnlyMe()) {
            return "Только ваши операции";
        }
        if (mode.isAll()) {
            return "Общая картина семьи: " + String.join(" и ", members.stream().map(MemberOption::name).toList());
        }
        String name = members.stream().filter(m -> m.id().equals(mode.memberId())).map(MemberOption::name)
                .findFirst().orElse("участника");
        return "Операции на счетах, которые " + name + " открывает для семьи";
    }

    private static String othersLegend(ViewMode mode, List<MemberOption> members) {
        if (mode.isOnlyMe() || members.size() < 2) {
            return null;
        }
        List<MemberOption> others = members.stream().filter(m -> !m.me()).toList();
        if (!mode.isAll()) {
            return others.stream().filter(m -> m.id().equals(mode.memberId())).map(MemberOption::name)
                    .findFirst().orElse(null);
        }
        return others.size() == 1 ? others.get(0).name() : "Другие участники";
    }

    /** 6 столбцов, заканчивая выбранным месяцем; высоты пропорциональны самому большому месяцу. */
    private List<ChartColumn> chart(Long userId, Set<Long> visible, YearMonth month) {
        YearMonth first = month.minusMonths(CHART_MONTHS - 1L);
        Map<YearMonth, BigDecimal[]> sums = new HashMap<>();
        if (!visible.isEmpty()) {
            for (MonthAuthorTotal t : transactionRepository.expensesByMonthAndAuthor(visible, first.atDay(1),
                    month.atEndOfMonth())) {
                BigDecimal[] pair = sums.computeIfAbsent(YearMonth.of(t.year(), t.month()),
                        k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                int index = t.userId().equals(userId) ? 0 : 1;
                pair[index] = pair[index].add(t.total());
            }
        }
        BigDecimal max = sums.values().stream().map(p -> p[0].add(p[1])).reduce(BigDecimal.ZERO, BigDecimal::max);
        List<ChartColumn> columns = new ArrayList<>();
        for (int i = 0; i < CHART_MONTHS; i++) {
            YearMonth ym = first.plusMonths(i);
            BigDecimal[] pair = sums.getOrDefault(ym, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            columns.add(new ChartColumn(DateFormat.monthShort(ym), MoneyFormat.formatWhole(pair[0].add(pair[1])),
                    height(pair[0], max), height(pair[1], max), ym.equals(month)));
        }
        return columns;
    }

    private static int height(BigDecimal value, BigDecimal max) {
        if (max.signum() == 0) {
            return 0;
        }
        return value.multiply(BigDecimal.valueOf(CHART_HEIGHT_PX)).divide(max, 0, RoundingMode.HALF_UP).intValue();
    }
}
