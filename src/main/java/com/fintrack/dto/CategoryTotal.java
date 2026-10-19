package com.fintrack.dto;

import java.math.BigDecimal;

public record CategoryTotal(Long categoryId, String name, BigDecimal total) {
}
