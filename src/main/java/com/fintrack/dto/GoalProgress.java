package com.fintrack.dto;

/** Прогресс цели для отчёта: процент накопленного и выполнен ли план месяца. */
public record GoalProgress(String name, int percent, boolean onTrack) {
}
