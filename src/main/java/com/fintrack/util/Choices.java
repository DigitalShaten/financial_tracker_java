package com.fintrack.util;

import java.util.List;

/**
 * Фиксированные списки значений для выпадающих списков в формах.
 */
public final class Choices {

    /** Валюты, доступные пользователю (код ISO 4217). */
    public static final List<String> CURRENCIES = List.of("BYN", "RUB", "EUR", "USD");

    /** Регулярное выражение для проверки валюты в формах. */
    public static final String CURRENCY_PATTERN = "BYN|RUB|EUR|USD";

    /** Часовые пояса, которые предлагаются в профиле. */
    public static final List<String> TIME_ZONES = List.of(
            "Europe/Minsk", "Europe/Moscow", "Europe/Kaliningrad", "Europe/Samara",
            "Asia/Yekaterinburg", "Asia/Novosibirsk", "Europe/Vilnius", "Europe/Riga",
            "Europe/Warsaw", "Europe/Kyiv", "Europe/Berlin", "Europe/London", "Asia/Almaty",
            "Asia/Tbilisi", "UTC");

    private Choices() {
    }
}
