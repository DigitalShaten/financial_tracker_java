package com.fintrack.dto;

public record MemberView(Long userId, String name, String initial, boolean me, boolean owner, String roleLabel,
                         boolean telegramLinked, String joinedText, String monthExpenseText) {
}
