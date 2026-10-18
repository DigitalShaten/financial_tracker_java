package com.fintrack.dto;

/** Карточка цели в списке: срок, прогресс к стоимости с учётом инфляции. */
public record GoalCard(Long id, String name, boolean family, String deadlineText, int percent,
                       String amountText, boolean active) {
}
