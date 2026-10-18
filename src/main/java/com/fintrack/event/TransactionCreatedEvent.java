package com.fintrack.event;

/**
 * Событие «создана новая операция». Слушатель проверяет бюджеты после коммита транзакции.
 */
public record TransactionCreatedEvent(Long transactionId, Long userId) {
}
