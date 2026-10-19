package com.fintrack.dto;

import com.fintrack.entity.TransactionType;

public record OperationView(Long id, String title, String comment, String accountName, String authorName,
                            String authorInitial, boolean mine, String categoryName, TransactionType type,
                            String signedAmountText, String shortDate) {

    /** CSS-класс суммы: доход зелёный, перевод серый. */
    public String amountClass() {
        return switch (type) {
            case INCOME -> "plus";
            case TRANSFER -> "transfer";
            case EXPENSE -> "";
        };
    }
}
