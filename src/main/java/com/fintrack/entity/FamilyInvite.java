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

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User user;

    @Column(name  = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt = OffsetDateTime.now();

    @Column(name  = "used_at", updatable = false)
    private OffsetDateTime usedAt = OffsetDateTime.now();
}
