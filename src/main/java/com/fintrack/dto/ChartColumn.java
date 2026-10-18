package com.fintrack.dto;

/**
 * Столбец графика «Расходы по месяцам»: высоты частей в пикселях (зелёная — вы, оранжевая — остальные).
 */
public record ChartColumn(String label, String totalText, int mineHeight, int othersHeight, boolean current) {
}
