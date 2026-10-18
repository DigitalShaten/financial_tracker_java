package com.fintrack.entity;

/**
 * Тип категории: доход или расход.
 */
public enum CategoryType {
    INCOME("Доход"),
    EXPENSE("Расход");

    private final String label;

    CategoryType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
