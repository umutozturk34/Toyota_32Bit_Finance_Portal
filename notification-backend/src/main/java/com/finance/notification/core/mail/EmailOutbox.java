package com.finance.notification.core.mail;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    public enum Status { PENDING, RELAYED, PROCESSING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String subject;

    @Column(name = "template_name", nullable = false, length = 64)
    private String templateName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode model;

    @Column(nullable = false, length = 16)
    private String theme;

    @Column(nullable = false, length = 8)
    private String locale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "relayed_at")
    private LocalDateTime relayedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (status == null) status = Status.PENDING;
        if (nextAttemptAt == null) nextAttemptAt = now;
    }
}
