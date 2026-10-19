package com.fintrack.telegram;

import com.fintrack.dto.CategoryTotal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Числа ежемесячного отчёта. Их считает {@code MonthlyReportService}, а {@link ReportTextGenerator}
 * только превращает в текст.
 *
 * @param title          "Отчёт за август 2026" или "Отчёт семьи за август 2026"
 * @param previousExpense расходы прошлого месяца (для сравнения), ноль - сравнения нет
 * @param previousMonthIn "июле" - для фразы "чем в июле"
 * @param budgetCount     сколько бюджетов задано на месяц (0 - блок бюджетов не выводится)
 */
public record MonthlyReport(String title, String currency, BigDecimal income, BigDecimal expense,
                            BigDecimal previousExpense, String previousMonthIn, BigDecimal savedToGoals,
                            List<CategoryTotal> topCategories, int budgetCount, List<OverBudget> overBudgets,
                            List<GoalLine> goals) {

    /** Превышенный бюджет: категория и на сколько процентов. */
    public record OverBudget(String category, int percent) {
    }

    /** Цель в отчёте: процент накопленного и идёт ли по плану. */
    public record GoalLine(String name, int percent, boolean onTrack) {
    }
}
