package com.fintrack.dto;

import java.util.List;

public record OperationsPage(List<DayGroup> days, String countText, String expenseText, String incomeText,
                             int page, int totalPages, String prevUrl, String nextUrl) {
}
