package com.fintrack.telegram;

import com.fintrack.dto.CategoryTotal;
import com.fintrack.util.MoneyFormat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateReportTextGeneratorTest {

    private final TemplateReportTextGenerator generator = new TemplateReportTextGenerator();

    @Test
    void reportFromMockupContainsSumsTopCategoriesBudgetsAndGoals() {
        MonthlyReport report = new MonthlyReport("Отчёт за август 2026", "BYN",
                new BigDecimal("4300"), new BigDecimal("2512.30"), new BigDecimal("2887.70"), "июле",
                new BigDecimal("1420"),
                List.of(new CategoryTotal(1L, "Продукты", new BigDecimal("498.10")),
                        new CategoryTotal(2L, "Квартира и счета", new BigDecimal("320")),
                        new CategoryTotal(3L, "Кафе и рестораны", new BigDecimal("284.60"))),
                6, List.of(new MonthlyReport.OverBudget("Кафе и рестораны", 18)),
                List.of(new MonthlyReport.GoalLine("Квартира", 19, true),
                        new MonthlyReport.GoalLine("Отпуск летом 2027", 42, false)));

        String text = plain(generator.generate(report));

        assertThat(text).isEqualTo("""
                Отчёт за август 2026

                Доходы: 4 300,00 BYN
                Расходы: 2 512,30 BYN (на 13% меньше, чем в июле)
                Отложено на цели: 1 420,00 BYN

                Больше всего потратили на:
                1. Продукты — 498,10
                2. Квартира и счета — 320,00
                3. Кафе и рестораны — 284,60

                Бюджеты: превышен «Кафе и рестораны» на 18%. Остальные категории в норме

                Цели:
                «Квартира» — 19%, идёте по плану
                «Отпуск летом 2027» — 42%""");
    }

    @Test
    void emptyBlocksAreNotPrinted() {
        MonthlyReport report = new MonthlyReport("Отчёт за август 2026", "BYN",
                new BigDecimal("100"), new BigDecimal("40"), BigDecimal.ZERO, "июле", BigDecimal.ZERO,
                List.of(new CategoryTotal(1L, "Продукты", new BigDecimal("40"))), 0, List.of(), List.of());

        String text = plain(generator.generate(report));

        assertThat(text).contains("Расходы: 40,00 BYN").doesNotContain("чем в", "Отложено", "Бюджеты", "Цели");
    }

    /** Неразрывные пробелы в суммах заменяем обычными, чтобы ожидаемый текст было удобно читать. */
    private static String plain(String text) {
        return text.replace(MoneyFormat.NBSP, ' ');
    }

    @Test
    void monthWithoutOperationsSaysSo() {
        MonthlyReport report = new MonthlyReport("Отчёт за август 2026", "BYN", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "июле", BigDecimal.ZERO, List.of(), 0, List.of(), List.of());

        assertThat(generator.generate(report)).isEqualTo("Отчёт за август 2026\n\nОпераций за месяц не было");
    }
}
