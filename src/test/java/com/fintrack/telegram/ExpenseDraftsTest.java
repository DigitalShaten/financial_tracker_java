package com.fintrack.telegram;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseDraftsTest {

    /** Часы, которые можно двигать вручную. */
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T10:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void draftGoesFromAmountToCommentAndExpiresAfter15Minutes() {
        MovableClock clock = new MovableClock();
        ExpenseDrafts drafts = new ExpenseDrafts(clock);

        drafts.start(1L, 5L, "Продукты");
        ExpenseDrafts.Draft draft = drafts.find(1L).orElseThrow();
        assertThat(draft.waitsForAmount()).isTrue();

        drafts.setAmount(1L, draft, new BigDecimal("46.80"));
        assertThat(drafts.find(1L).orElseThrow().waitsForAmount()).isFalse();
        assertThat(drafts.find(2L)).isEmpty();   // у другого чата своего черновика нет

        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertThat(drafts.find(1L)).isEmpty();
    }
}
