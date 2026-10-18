package com.fintrack.dto;

/** Цель на обзоре: прогресс «накоплено из стоимости». */
public record GoalMini(Long id, String name, boolean family, String savedText, String priceText, int percent) {
}
