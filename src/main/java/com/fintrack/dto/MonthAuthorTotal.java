package com.fintrack.dto;

import java.math.BigDecimal;

public record MonthAuthorTotal(Integer year, Integer month, Long userId, BigDecimal total) {
}
