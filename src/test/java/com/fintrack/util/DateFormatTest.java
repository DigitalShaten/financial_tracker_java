package com.fintrack.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class DateFormatTest {

    @Test
    void russianDates() {
        LocalDate date = LocalDate.of(2026, 9, 18);
        assertThat(DateFormat.dayMonth(date)).isEqualTo("18 сентября");
        assertThat(DateFormat.dayMonthWeekday(date)).isEqualTo("18 сентября, пятница");
        assertThat(DateFormat.monthTitle(YearMonth.of(2026, 9))).isEqualTo("Сентябрь 2026");
        assertThat(DateFormat.monthLower(YearMonth.of(2026, 8))).isEqualTo("август 2026");
        assertThat(DateFormat.monthShort(YearMonth.of(2026, 4))).isEqualTo("Апр");
        assertThat(DateFormat.monthPrepositional(Month.JULY)).isEqualTo("июле");
    }

    @Test
    void plurals() {
        assertThat(Plurals.count(1, "счёт", "счёта", "счетов")).isEqualTo("1 счёт");
        assertThat(Plurals.count(3, "счёт", "счёта", "счетов")).isEqualTo("3 счёта");
        assertThat(Plurals.count(11, "счёт", "счёта", "счетов")).isEqualTo("11 счетов");
        assertThat(Plurals.count(22, "счёт", "счёта", "счетов")).isEqualTo("22 счёта");
    }
}
