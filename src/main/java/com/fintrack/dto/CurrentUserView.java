package com.fintrack.dto;

import java.util.List;

public record CurrentUserView(Long id, String name, String currency, String familyName,
                              boolean inFamily, boolean familyOwner, boolean admin,
                              List<MemberChip> familyMembers) {

    public String initial() {
        return initialOf(name);
    }

    /** Имена участников семьи через запятую: "Дмитрий, Анна". */
    public String familyMemberNames() {
        return String.join(", ", familyMembers.stream().map(MemberChip::name).toList());
    }

    public static String initialOf(String name) {
        return name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
    }
}
