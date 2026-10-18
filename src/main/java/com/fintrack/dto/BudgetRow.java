package com.fintrack.dto;

/**
 * Строка исполнения бюджета: «412 / 500 BYN», полоса (до 100%) и подпись.
 */
public record BudgetRow(String categoryName, String spentText, String limitText, int percent,
                        boolean over, String note) {
}
