package com.fintrack.service;

import com.fintrack.dto.RealismStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GoalRealismTest {

    private final GoalService service = new GoalService(null, null, null, null, null, null, null);

    private RealismStatus assess(String payment, String freeCash) {
        return service.assessRealism(new BigDecimal(payment), new BigDecimal(freeCash), "BYN");
    }

    @Test
    void upToSeventyFivePercentIsAchievable() {
        RealismStatus status = assess("1382", "1900");

        assertThat(status.ok()).isTrue();
        assertThat(status.title()).isEqualTo("Цель достижима");
        assertThat(status.text()).isEqualTo("Взнос — 73% от среднего свободного остатка за 3 месяца (1 900 BYN в месяц).");
    }

    @Test
    void exactlySeventyFivePercentIsStillAchievable() {
        assertThat(assess("750", "1000").title()).isEqualTo("Цель достижима");
    }

    @Test
    void upToHundredPercentIsTight() {
        RealismStatus status = assess("900", "1000");

        assertThat(status.ok()).isFalse();
        assertThat(status.title()).isEqualTo("Получится, но впритык");
        assertThat(status.text()).startsWith("Взнос забирает 90% свободных денег.");
    }

    @Test
    void moreThanFreeCashIsNotAchievable() {
        RealismStatus status = assess("1500", "1000");

        assertThat(status.title()).isEqualTo("При текущих тратах не успеть");
        assertThat(status.text()).startsWith("Не хватает 500 BYN в месяц.");
    }

    @Test
    void noFreeCashGivesWarning() {
        RealismStatus status = assess("100", "-20");

        assertThat(status.ok()).isFalse();
        assertThat(status.title()).isEqualTo("Свободных денег в последние месяцы не оставалось");
        assertThat(assess("100", "0").title()).isEqualTo("Свободных денег в последние месяцы не оставалось");
    }
}
