package com.fintrack.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Единый формат денег в интерфейсе и в боте: «1 286,40 BYN».
 * Разделитель тысяч — неразрывный пробел, десятичный — запятая.
 */
public final class MoneyFormat {

    /** Неразрывный пробел: число и валюта не разъезжаются по разным строкам. */
    public static final char NBSP = ' ';

    private MoneyFormat() {
    }

    /** «1 286,40». */
    public static String format(BigDecimal value) {
        return formatter("#,##0.00").format(value.setScale(2, RoundingMode.HALF_UP));
    }

    /** «1 286,40 BYN». */
    public static String format(BigDecimal value, String currency) {
        return format(value) + NBSP + currency;
    }

    /** Без копеек: «1 286» (для полос бюджета и целей, как в макете). */
    public static String formatWhole(BigDecimal value) {
        return formatter("#,##0").format(value.setScale(0, RoundingMode.HALF_UP));
    }

    /** «1 286 BYN». */
    public static String formatWhole(BigDecimal value, String currency) {
        return formatWhole(value) + NBSP + currency;
    }

    private static DecimalFormat formatter(String pattern) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(NBSP);
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('−');
        // DecimalFormat не потокобезопасен, поэтому создаётся на каждый вызов
        return new DecimalFormat(pattern, symbols);
    }
}
