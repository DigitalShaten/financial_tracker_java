package com.fintrack.service.calc;

import java.math.BigDecimal;

/**
 * Результат расчёта цели.
 *
 * @param futureCost         стоимость к дате цели с учётом инфляции
 * @param monthlyPayment     сколько откладывать в месяц
 * @param totalContributions сумма всех будущих взносов
 * @param interestEarned     сколько добавят проценты по вкладу
 * @param inflationIncrease  удорожание из-за инфляции
 */
public record GoalPlan(BigDecimal futureCost, BigDecimal monthlyPayment,
                       BigDecimal totalContributions, BigDecimal interestEarned,
                       BigDecimal inflationIncrease) {
}
