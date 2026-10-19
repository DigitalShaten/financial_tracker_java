package com.fintrack.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * "Можно потратить сегодня": свободные деньги месяца делятся на оставшиеся дни (включая сегодня).
 * <pre>
 * свободно  = доходы месяца - плановые взносы на цели - расходы месяца
 * дней      = дней в месяце - сегодняшнее число + 1
 * результат = max(свободно, 0) / дней, округление вниз до 0,01
 * </pre>
 */
public final class DailyAllowanceCalculator {

    private DailyAllowanceCalculator() {
    }

    public static BigDecimal calculate(BigDecimal monthIncome, BigDecimal plannedSavings,
                                       BigDecimal monthExpense, LocalDate today) {
        int daysLeft = daysLeft(today);
        BigDecimal free = monthIncome.subtract(plannedSavings).subtract(monthExpense);
        if (free.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return free.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.DOWN);
    }

    /** Сколько дней осталось до конца месяца, включая сегодняшний. */
    public static int daysLeft(LocalDate today) {
        return today.lengthOfMonth() - today.getDayOfMonth() + 1;
    }
}
