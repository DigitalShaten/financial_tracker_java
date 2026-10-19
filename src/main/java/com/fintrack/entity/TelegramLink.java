package com.fintrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "telegram_links")
@Getter
@Setter
@NoArgsConstructor
public class TelegramLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "chat_id", unique = true)
    private Long chatId;

    @Column(name = "link_code", length = 16)
    private String linkCode;

    @Column(name = "code_expires_at")
    private OffsetDateTime codeExpiresAt;

    @Column(name = "linked_at")
    private OffsetDateTime linkedAt;

    public boolean isLinked() {
        return chatId != null;
    }
}
