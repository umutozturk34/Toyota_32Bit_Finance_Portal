package com.finance.notification.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_unread", columnList = "user_sub, read_at"),
        @Index(name = "idx_notifications_user_created", columnList = "user_sub, created_at DESC"),
        @Index(name = "idx_notifications_expires", columnList = "expires_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isUnread() {
        return readAt == null;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean belongsTo(String candidateUserSub) {
        return userSub.equals(candidateUserSub);
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }

    public static Notification create(String userSub,
                                      NotificationType type,
                                      String title,
                                      String body,
                                      Map<String, Object> metadata,
                                      LocalDateTime expiresAt) {
        return Notification.builder()
                .userSub(userSub)
                .type(type)
                .title(title)
                .body(body)
                .metadata(metadata)
                .expiresAt(expiresAt)
                .build();
    }
}
