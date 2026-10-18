package com.fintrack.dto;

import java.math.BigDecimal;

/** Сколько внёс участник в цель. */
public record ContributorTotal(Long userId, String name, BigDecimal total) {
}
