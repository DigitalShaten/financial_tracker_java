package com.fintrack.dto;

import java.math.BigDecimal;

public record Contributor(String name, String initial, boolean mine, String amountText, int percent,
                          BigDecimal weight) {
}
