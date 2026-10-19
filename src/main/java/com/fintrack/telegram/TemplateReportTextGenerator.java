package com.fintrack.telegram;

import com.fintrack.dto.CategoryTotal;
import com.fintrack.util.MoneyFormat;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Текст отчёта по шаблону. Пустые блоки (нет бюджетов, нет целей) не выводятся.
 */
@Component
public class TemplateReportTextGenerator implements ReportTextGenerator {

    @Override
    public String generate(MonthlyReport r) {
        List<String> blocks = new ArrayList<>();
        blocks.add(r.title());

        if (r.income().signum() == 0 && r.expense().signum() == 0) {
            blocks.add("Операций за месяц не было");
        } else {
            StringBuilder totals = new StringBuilder();
            totals.append("Доходы: ").append(MoneyFormat.format(r.income(), r.currency())).append('\n');
            totals.append("Расходы: ").append(MoneyFormat.format(r.expense(), r.currency())).append(comparison(r));
            if (r.savedToGoals().signum() > 0) {
                totals.append('\n').append("Отложено на цели: ").append(MoneyFormat.format(r.savedToGoals(), r.currency()));
            }
            blocks.add(totals.toString());
        }

        if (!r.topCategories().isEmpty()) {
            StringBuilder top = new StringBuilder("Больше всего потратили на:");
            int i = 1;
            for (CategoryTotal c : r.topCategories()) {
                top.append('\n').append(i++).append(". ").append(c.name()).append(" — ").append(MoneyFormat.format(c.total()));
            }
            blocks.add(top.toString());
        }

        if (r.budgetCount() > 0) {
            blocks.add(budgets(r));
        }

        if (!r.goals().isEmpty()) {
            StringBuilder goals = new StringBuilder("Цели:");
            for (MonthlyReport.GoalLine g : r.goals()) {
                goals.append('\n').append('«').append(g.name()).append("» — ").append(g.percent()).append('%');
                if (g.onTrack()) {
                    goals.append(", идёте по плану");
                }
            }
            blocks.add(goals.toString());
        }
        return String.join("\n\n", blocks);
    }

    /** " (на 13% меньше, чем в июле)". */
    private static String comparison(MonthlyReport r) {
        if (r.previousExpense() == null || r.previousExpense().signum() == 0) {
            return "";
        }
        int change = r.expense().subtract(r.previousExpense()).multiply(BigDecimal.valueOf(100))
                .divide(r.previousExpense(), 0, RoundingMode.HALF_UP).intValue();
        if (change == 0) {
            return " (как в " + r.previousMonthIn() + ")";
        }
        return " (на " + Math.abs(change) + "% " + (change < 0 ? "меньше" : "больше") + ", чем в " + r.previousMonthIn() + ")";
    }

    /** "Бюджеты: превышен "Кафе и рестораны" на 18%. Остальные категории в норме". */
    private static String budgets(MonthlyReport r) {
        if (r.overBudgets().isEmpty()) {
            return "Бюджеты: все категории в норме";
        }
        List<String> items = r.overBudgets().stream()
                .map(o -> "«" + o.category() + "» на " + o.percent() + "%").toList();
        String text = "Бюджеты: " + (items.size() == 1 ? "превышен " : "превышены ") + String.join(", ", items);
        return r.overBudgets().size() < r.budgetCount() ? text + ". Остальные категории в норме" : text;
    }
}
