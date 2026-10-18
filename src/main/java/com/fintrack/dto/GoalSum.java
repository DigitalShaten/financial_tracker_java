package com.fintrack.dto;

import java.math.BigDecimal;

/** «Накоплено» по цели (сумма пополнений). */
public record GoalSum(Long goalId, BigDecimal total) {
}
