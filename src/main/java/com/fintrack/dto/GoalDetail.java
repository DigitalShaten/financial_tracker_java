package com.fintrack.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Выбранная цель: значения ползунков, расчёт плана, реалистичность и вклад участников.
 */
public record GoalDetail(Long id, String name, boolean family, boolean canEdit,
                         BigDecimal price, int years, BigDecimal inflation, BigDecimal rate, BigDecimal priceMax,
                         BigDecimal saved, String savedText, BigDecimal freeCash, String currentMonth,
                         String paymentText, String monthsText, String futureText, String contributionsText,
                         String interestText, String inflationText, RealismStatus status,
                         List<Contributor> contributors, String contribNote, String priceMaxText) {
}
