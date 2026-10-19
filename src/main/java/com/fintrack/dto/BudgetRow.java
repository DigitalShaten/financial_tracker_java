package com.fintrack.dto;

public record BudgetRow(String categoryName, String spentText, String limitText, int percent,
                        boolean over, String note) {
}
