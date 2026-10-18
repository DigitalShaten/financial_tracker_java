package com.fintrack.dto;

import java.math.BigDecimal;

/** Участник в панели «Кто сколько внёс»; weight — доля для полосы. */
public record Contributor(String name, String initial, boolean mine, String amountText, int percent,
                          BigDecimal weight) {
}
