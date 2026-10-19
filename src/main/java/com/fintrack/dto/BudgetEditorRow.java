package com.fintrack.dto;

import java.math.BigDecimal;

public record BudgetEditorRow(Long categoryId, String categoryName, BigDecimal limit, BudgetRow row) {
}
