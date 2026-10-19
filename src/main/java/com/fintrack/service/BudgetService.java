package com.fintrack.service;

import com.fintrack.dto.BudgetEditorRow;
import com.fintrack.dto.BudgetRow;
import com.fintrack.dto.CategoryTotal;
import com.fintrack.dto.CategoryView;
import com.fintrack.entity.Budget;
import com.fintrack.entity.Category;
import com.fintrack.entity.TransactionType;
import com.fintrack.entity.User;
import com.fintrack.repository.BudgetRepository;
import com.fintrack.repository.TransactionRepository;
import com.fintrack.repository.UserRepository;
import com.fintrack.util.MoneyFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

/**
 * Месячные лимиты расходов по категориям. Бюджет = (пользователь, категория, месяц) -> лимит;
 * месяц хранится как первое число месяца.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final UserRepository userRepository;

    /**
     * Установить лимит (upsert: повторная установка обновляет лимит). Пустой или нулевой лимит
     * убирает бюджет.
     */
    @Transactional
    public void setLimit(Long userId, Long categoryId, YearMonth month, BigDecimal limit) {
        Category category = categoryService.getAvailable(userId, categoryId, TransactionType.EXPENSE);
        var existing = budgetRepository.findByUser_IdAndCategory_IdAndMonth(userId, categoryId, month.atDay(1));
        if (limit == null || limit.signum() == 0) {
            existing.ifPresent(budgetRepository::delete);
            return;
        }
        Budget budget = existing.orElseGet(() -> {
            Budget b = new Budget();
            b.setUser(userRepository.getReferenceById(userId));
            b.setCategory(category);
            b.setMonth(month.atDay(1));
            return b;
        });
        budget.setLimitAmount(limit);
        budgetRepository.save(budget);
    }

    /** Копирует бюджеты прошлого месяца в указанный (уже заданные лимиты не трогает). Возвращает число скопированных. */
    @Transactional
    public int copyPrevious(Long userId, YearMonth month) {
        List<Budget> previous = budgetRepository.findAllWithCategories(List.of(userId), month.minusMonths(1).atDay(1));
        int copied = 0;
        for (Budget old : previous) {
            if (budgetRepository.findByUser_IdAndCategory_IdAndMonth(userId, old.getCategory().getId(),
                    month.atDay(1)).isEmpty()) {
                Budget copy = new Budget();
                copy.setUser(old.getUser());
                copy.setCategory(old.getCategory());
                copy.setMonth(month.atDay(1));
                copy.setLimitAmount(old.getLimitAmount());
                budgetRepository.save(copy);
                copied++;
            }
        }
        return copied;
    }

    /**
     * Строки исполнения бюджетов для обзора. Для нескольких пользователей (режимы "Вместе" и участника)
     * лимиты складываются по названию категории, расходы считаются по видимым счетам.
     */
    @Transactional(readOnly = true)
    public List<BudgetRow> rows(Collection<Long> userIds, Set<Long> visibleAccounts, YearMonth month, String currency) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> limits = new TreeMap<>();
        for (Budget b : budgetRepository.findAllWithCategories(userIds, month.atDay(1))) {
            limits.merge(b.getCategory().getName(), b.getLimitAmount(), BigDecimal::add);
        }
        Map<String, BigDecimal> spent = spentByCategoryName(visibleAccounts, month);
        return limits.entrySet().stream()
                .map(e -> row(e.getKey(), spent.getOrDefault(e.getKey(), BigDecimal.ZERO), e.getValue(), currency))
                .toList();
    }

    /** Редактор бюджетов на странице "Счета и бюджеты": все доступные категории расходов пользователя. */
    @Transactional(readOnly = true)
    public List<BudgetEditorRow> editorRows(Long userId, Set<Long> ownAccounts, YearMonth month) {
        String currency = userRepository.findById(userId).map(User::getCurrency).orElse("BYN");
        Map<Long, BigDecimal> limits = new HashMap<>();
        for (Budget b : budgetRepository.findAllWithCategories(List.of(userId), month.atDay(1))) {
            limits.put(b.getCategory().getId(), b.getLimitAmount());
        }
        Map<Long, BigDecimal> spent = new HashMap<>();
        if (!ownAccounts.isEmpty()) {
            for (CategoryTotal t : transactionRepository.expensesByCategory(ownAccounts, month.atDay(1), month.atEndOfMonth())) {
                spent.put(t.categoryId(), t.total());
            }
        }
        return categoryService.availableExpenses(userId).stream()
                .map((CategoryView c) -> {
                    BigDecimal limit = limits.get(c.id());
                    BudgetRow row = limit == null ? null
                            : row(c.name(), spent.getOrDefault(c.id(), BigDecimal.ZERO), limit, currency);
                    return new BudgetEditorRow(c.id(), c.name(), limit, row);
                })
                .toList();
    }

    private Map<String, BigDecimal> spentByCategoryName(Set<Long> accounts, YearMonth month) {
        Map<String, BigDecimal> spent = new HashMap<>();
        if (accounts.isEmpty()) {
            return spent;
        }
        for (CategoryTotal t : transactionRepository.expensesByCategory(accounts, month.atDay(1), month.atEndOfMonth())) {
            spent.merge(t.name(), t.total(), BigDecimal::add);
        }
        return spent;
    }

    /** Подпись: "Осталось N {валюта}" / "Бюджет исчерпан" / "Превышен на N%". */
    static BudgetRow row(String name, BigDecimal spent, BigDecimal limit, String currency) {
        boolean over = spent.compareTo(limit) > 0;
        int percent = spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100)).intValue();
        String note;
        if (over) {
            int overPercent = spent.subtract(limit).multiply(BigDecimal.valueOf(100))
                    .divide(limit, 0, RoundingMode.HALF_UP).intValue();
            note = "Превышен на " + overPercent + "%";
        } else if (limit.subtract(spent).compareTo(BigDecimal.ONE) < 0) {
            note = "Бюджет исчерпан";
        } else {
            note = "Осталось " + MoneyFormat.formatWhole(limit.subtract(spent), currency);
        }
        return new BudgetRow(name, MoneyFormat.formatWhole(spent), MoneyFormat.formatWhole(limit, currency),
                percent, over, note);
    }
}
