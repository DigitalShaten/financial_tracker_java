package com.fintrack.dto;

import java.util.List;

/**
 * Всё, что нужно странице «Обзор». Деньги уже отформатированы.
 *
 * @param todayText      «Можно потратить сегодня» или null, если месяц завершён
 * @param todayHint      подпись под суммой («До конца месяца 13 дней» / «Месяц завершён»)
 * @param prevMonth      параметр month для стрелки «назад»
 * @param nextMonth      параметр month для стрелки «вперёд»; null для текущего месяца
 */
public record DashboardView(String greetingName, String monthTitle, String month, String prevMonth, String nextMonth,
                            String view, List<ViewOption> viewOptions, String viewNote,
                            String balanceText, String accountsText, String incomeText, String expenseText,
                            String expenseShareText, String todayText, String todayHint,
                            List<BudgetRow> budgets, List<GoalMini> goals, List<OperationView> recent,
                            boolean showAuthors, List<ChartColumn> chart, String legendMine, String legendOthers) {
}
