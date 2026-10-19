package com.fintrack.dto;

import java.util.List;

public record DashboardView(String greetingName, String monthTitle, String month, String prevMonth, String nextMonth,
                            String view, List<ViewOption> viewOptions, String viewNote,
                            String balanceText, String accountsText, String incomeText, String expenseText,
                            String expenseShareText, String todayText, String todayHint,
                            List<BudgetRow> budgets, List<GoalMini> goals, List<OperationView> recent,
                            boolean showAuthors, List<ChartColumn> chart, String legendMine, String legendOthers,
                            String bannerTitle, String bannerText, String bannerAction) {
}
