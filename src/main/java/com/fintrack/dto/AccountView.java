package com.fintrack.dto;

import com.fintrack.entity.AccountType;

public record AccountView(Long id, String name, AccountType type, String typeLabel, String balanceText,
                          boolean shared, boolean archived) {
}
