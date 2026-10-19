package com.fintrack.dto;

import java.time.LocalDate;

public record TransactionFilter(LocalDate from, LocalDate to, Long accountId, Long categoryId,
                                Long memberId, String query, Integer page) {

    public int pageIndex() {
        return page == null || page < 0 ? 0 : page;
    }
}
