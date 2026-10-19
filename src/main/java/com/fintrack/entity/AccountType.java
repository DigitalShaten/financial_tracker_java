package com.fintrack.entity;

public enum AccountType {
    CASH("Наличные"),
    CARD("Карта"),
    SAVINGS("Накопительный");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
