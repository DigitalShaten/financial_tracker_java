package com.fintrack.service.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Расчёт ежемесячного взноса на цель - чистый класс без Spring.
 * <ol>
 *   <li>Стоимость к дате цели: {@code future = price * (1 + inflation)^years}.</li>
 *   <li>Взнос - формула аннуитета с учётом процентов, которые набегают на уже накопленное
 *       и на сами взносы: {@code payment = (future - saved * (1+r)^n) * r / ((1+r)^n - 1)},
 *       где r - месячная ставка, n - число месяцев. При нулевой ставке - простое деление.</li>
 * </ol>
 * Внутренние степени считаются в double (исключение из правила "деньги - только BigDecimal"),
 * результат округляется до копеек.
 */
public final class GoalCalculator {

    private GoalCalculator() {
    }

    public static GoalPlan calculate(BigDecimal priceToday, int months,
                                     BigDecimal inflationPercent, BigDecimal annualRatePercent,
                                     BigDecimal alreadySaved) {
        if (months <= 0) {
            throw new IllegalArgumentException("Срок цели должен быть больше нуля");
        }
        double price = priceToday.doubleValue();
        double saved = alreadySaved.doubleValue();
        double years = months / 12.0;

        double future = price * Math.pow(1 + inflationPercent.doubleValue() / 100, years);
        double r = annualRatePercent.doubleValue() / 100 / 12;
        double growth = Math.pow(1 + r, months);

        double payment = (r == 0)
                ? (future - saved) / months
                : (future - saved * growth) * r / (growth - 1);
        payment = Math.max(payment, 0);

        double contributions = payment * months;
        double interest = Math.max(future - saved - contributions, 0);

        return new GoalPlan(money(future), money(payment), money(contributions),
                money(interest), money(future - price));
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
