package com.fintrack.dto;

/**
 * Участник семьи для переключателей и фильтров.
 */
public record MemberOption(Long id, String name, boolean me) {

    public String initial() {
        return CurrentUserView.initialOf(name);
    }
}
