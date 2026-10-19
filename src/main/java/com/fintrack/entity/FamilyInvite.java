package com.fintrack.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "family_invites")
@Getter
@Setter
@NoArgsConstructor
public class FamilyInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id")
    private Family family;

    /** Формат XXX-XXX. */
    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Момент использования; null - код ещё не использован. */
    @Column(name = "used_at")
    private OffsetDateTime usedAt;
}
