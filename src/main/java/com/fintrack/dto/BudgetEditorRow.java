package com.fintrack.dto;

import java.math.BigDecimal;

/**
 * Категория расходов в редакторе бюджетов: текущий лимит (может отсутствовать) и исполнение.
 */
public record BudgetEditorRow(Long categoryId, String categoryName, BigDecimal limit, BudgetRow row) {
}
