package com.fintrack.dto;

public record MemberOption(Long id, String name, boolean me) {

    public String initial() {
        return CurrentUserView.initialOf(name);
    }
}
