package com.fintrack.dto;

import com.fintrack.entity.AccountType;

/**
 * Счёт для отображения в списке.
 */
public record AccountView(Long id, String name, AccountType type, String typeLabel, String balanceText,
                          boolean shared, boolean archived) {
}
