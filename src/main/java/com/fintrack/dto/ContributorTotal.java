package com.fintrack.dto;

import java.math.BigDecimal;

public record ContributorTotal(Long userId, String name, BigDecimal total) {
}
