package com.fintrack.util;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Единый формат дат на русском: «18 сентября», «18 сентября, пятница», «Сентябрь 2026».
 */
public final class DateFormat {

    public static final Locale RU = Locale.forLanguageTag("ru");

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", RU);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", RU);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEEE", RU);
    private static final DateTimeFormatter MONTH_STANDALONE = DateTimeFormatter.ofPattern("LLLL", RU);

    /** Предложный падеж для фраз вида «чем в июле». */
    private static final String[] PREPOSITIONAL = {"январе", "феврале", "марте", "апреле", "мае", "июне",
            "июле", "августе", "сентябре", "октябре", "ноябре", "декабре"};

    private DateFormat() {
    }

    /** «18 сентября». */
    public static String dayMonth(LocalDate date) {
        return DAY_MONTH.format(date);
    }

    /** «3 сентября 2026». */
    public static String dayMonthYear(LocalDate date) {
        return DAY_MONTH_YEAR.format(date);
    }

    /** «18 сентября, пятница». */
    public static String dayMonthWeekday(LocalDate date) {
        return dayMonth(date) + ", " + WEEKDAY.format(date);
    }

    /** «сентябрь». */
    public static String monthName(Month month) {
        return MONTH_STANDALONE.format(YearMonth.of(2000, month));
    }

    /** «Сентябрь 2026» — заголовок месяца. */
    public static String monthTitle(YearMonth month) {
        return capitalize(monthName(month.getMonth())) + " " + month.getYear();
    }

    /** «сентябрь 2026» — внутри фразы. */
    public static String monthLower(YearMonth month) {
        return monthName(month.getMonth()) + " " + month.getYear();
    }

    /** «Сен» — подпись столбца графика. */
    public static String monthShort(YearMonth month) {
        return capitalize(monthName(month.getMonth()).substring(0, 3));
    }

    /** «июле». */
    public static String monthPrepositional(Month month) {
        return PREPOSITIONAL[month.getValue() - 1];
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(RU) + text.substring(1);
    }
}
