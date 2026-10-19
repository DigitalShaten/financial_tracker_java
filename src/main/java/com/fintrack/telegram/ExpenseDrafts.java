package com.fintrack.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Незаконченный ввод расхода в боте: категория -> сумма -> комментарий.
 * Хранится в памяти, после перезапуска приложения ввод начинается заново.
 */
@Component
@RequiredArgsConstructor
public class ExpenseDrafts {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<Long, Draft> drafts = new ConcurrentHashMap<>();

    /** Черновик: amount == null - ждём сумму, иначе ждём комментарий. */
    public record Draft(Long categoryId, String categoryName, BigDecimal amount, Instant startedAt) {

        public boolean waitsForAmount() {
            return amount == null;
        }
    }

    public void start(long chatId, Long categoryId, String categoryName) {
        drafts.put(chatId, new Draft(categoryId, categoryName, null, Instant.now(clock)));
    }

    public void setAmount(long chatId, Draft draft, BigDecimal amount) {
        drafts.put(chatId, new Draft(draft.categoryId(), draft.categoryName(), amount, draft.startedAt()));
    }

    /** Черновик, если он есть и не старше 15 минут. */
    public Optional<Draft> find(long chatId) {
        Draft draft = drafts.get(chatId);
        if (draft == null) {
            return Optional.empty();
        }
        if (draft.startedAt().plus(TTL).isBefore(Instant.now(clock))) {
            drafts.remove(chatId);
            return Optional.empty();
        }
        return Optional.of(draft);
    }

    public void clear(long chatId) {
        drafts.remove(chatId);
    }
}
