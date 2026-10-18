package com.fintrack.dto;

import com.fintrack.entity.TransactionType;

/**
 * Операция в списке. {@code mine == false} — операция другого участника семьи: только просмотр.
 */
public record OperationView(Long id, String title, String comment, String accountName, String authorName,
                            String authorInitial, boolean mine, String categoryName, TransactionType type,
                            String signedAmountText, String shortDate) {

    /** CSS-класс суммы из макета: доход зелёный, перевод серый. */
    public String amountClass() {
        return switch (type) {
            case INCOME -> "plus";
            case TRANSFER -> "transfer";
            case EXPENSE -> "";
        };
    }
}
