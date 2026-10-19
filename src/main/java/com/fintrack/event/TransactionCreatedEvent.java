package com.fintrack.event;

public record TransactionCreatedEvent(Long transactionId, Long userId) {
}
