package com.fintrack.dto;

import java.util.List;

public record DayGroup(String label, String expenseText, List<OperationView> items) {
}
