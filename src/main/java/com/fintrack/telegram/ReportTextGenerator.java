package com.fintrack.telegram;

/**
 * Превращает числа отчёта в текст. Точка расширения: можно добавить реализацию с выводами от ИИ.
 */
public interface ReportTextGenerator {

    String generate(MonthlyReport report);
}
