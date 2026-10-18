package com.fintrack.dto;

import java.math.BigDecimal;

/** Сумма расходов за месяц по автору операций (для графика «Расходы по месяцам»). */
public record MonthAuthorTotal(Integer year, Integer month, Long userId, BigDecimal total) {
}
