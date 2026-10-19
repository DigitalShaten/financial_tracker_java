package com.fintrack.service.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GoalCalculatorTest {

    private static final BigDecimal CENT = new BigDecimal("0.01");

    @Test
    void apartmentExampleFromMockup() {
        GoalPlan plan = GoalCalculator.calculate(new BigDecimal("95000"), 60,
                new BigDecimal("5"), new BigDecimal("6"), new BigDecimal("18400"));

        assertThat(plan.futureCost()).isCloseTo(new BigDecimal("121246.75"), within(CENT));
        assertThat(plan.monthlyPayment()).isCloseTo(new BigDecimal("1382.08"), within(CENT));
    }

    @Test
    void zeroRateIsSimpleDivision() {
        GoalPlan plan = GoalCalculator.calculate(new BigDecimal("1200"), 12,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(plan.monthlyPayment()).isEqualByComparingTo("100.00");
    }

    @Test
    void alreadyReachedGoalNeedsNoPayment() {
        GoalPlan plan = GoalCalculator.calculate(new BigDecimal("1000"), 12,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1500"));

        assertThat(plan.monthlyPayment()).isEqualByComparingTo("0");
    }

    @Test
    void zeroMonthsIsRejected() {
        assertThatThrownBy(() -> GoalCalculator.calculate(BigDecimal.TEN, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interestAndInflationBreakdownAddUp() {
        GoalPlan plan = GoalCalculator.calculate(new BigDecimal("95000"), 60,
                new BigDecimal("5"), new BigDecimal("6"), new BigDecimal("18400"));

        assertThat(plan.inflationIncrease()).isCloseTo(new BigDecimal("26246.75"), within(CENT));
        // накоплено + взносы + проценты = стоимость с инфляцией
        assertThat(new BigDecimal("18400").add(plan.totalContributions()).add(plan.interestEarned()))
                .isCloseTo(plan.futureCost(), within(new BigDecimal("0.05")));
    }
}
