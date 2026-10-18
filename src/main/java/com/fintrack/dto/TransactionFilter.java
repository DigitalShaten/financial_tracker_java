package com.fintrack.dto;

import java.time.LocalDate;

/**
 * Фильтр списка операций из GET-параметров. Пустой период означает текущий месяц.
 */
public record TransactionFilter(LocalDate from, LocalDate to, Long accountId, Long categoryId,
                                Long memberId, String query, Integer page) {

    public int pageIndex() {
        return page == null || page < 0 ? 0 : page;
    }
}
