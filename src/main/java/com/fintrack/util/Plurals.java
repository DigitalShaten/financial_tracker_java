package com.fintrack.util;

/**
 * Склонение слов после числительных: 1 счёт, 2 счёта, 5 счетов.
 */
public final class Plurals {

    private Plurals() {
    }

    public static String of(long n, String one, String few, String many) {
        long abs = Math.abs(n);
        long lastTwo = abs % 100;
        long last = abs % 10;
        if (lastTwo >= 11 && lastTwo <= 14) {
            return many;
        }
        if (last == 1) {
            return one;
        }
        if (last >= 2 && last <= 4) {
            return few;
        }
        return many;
    }

    /** Число вместе со словом: "3 счёта". */
    public static String count(long n, String one, String few, String many) {
        return n + " " + of(n, one, few, many);
    }
}
