package com.fintrack.dto;

import java.math.BigDecimal;

/** Сумма расходов по категории (результат агрегирующего запроса). */
public record CategoryTotal(Long categoryId, String name, BigDecimal total) {
}
