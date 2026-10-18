package com.fintrack.dto;

import java.util.List;

/**
 * Операции одного дня: «18 сентября, пятница» и сумма расходов за день (null — расходов не было).
 */
public record DayGroup(String label, String expenseText, List<OperationView> items) {
}
