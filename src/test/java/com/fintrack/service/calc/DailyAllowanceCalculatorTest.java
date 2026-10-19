package com.fintrack.service.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DailyAllowanceCalculatorTest {

    @Test
    void splitsFreeMoneyByRemainingDays() {
        // 30 сентября - последний день, 18-го осталось 13 дней включая сегодня
        BigDecimal result = DailyAllowanceCalculator.calculate(
                new BigDecimal("4300"), new BigDecimal("1400"), new BigDecimal("2391.20"),
                LocalDate.of(2026, 9, 18));

        assertThat(result).isEqualByComparingTo("39.13"); // 508.80 / 13 = 39.138 -> 39.13
    }

    @Test
    void neverNegative() {
        BigDecimal result = DailyAllowanceCalculator.calculate(
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("500"),
                LocalDate.of(2026, 9, 18));

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void lastDayOfMonthUsesOneDay() {
        assertThat(DailyAllowanceCalculator.daysLeft(LocalDate.of(2026, 9, 30))).isEqualTo(1);
        assertThat(DailyAllowanceCalculator.calculate(new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2026, 9, 30))).isEqualByComparingTo("50.00");
    }
}
