package com.finance.notification.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "email_price_alerts", nullable = false)
    private boolean emailPriceAlerts;

    @Column(name = "inapp_price_alerts", nullable = false)
    private boolean inappPriceAlerts;

    @Column(name = "email_watchlist", nullable = false)
    private boolean emailWatchlist;

    @Column(name = "inapp_watchlist", nullable = false)
    private boolean inappWatchlist;

    @Column(name = "email_reports", nullable = false)
    private boolean emailReports;

    @Column(name = "inapp_reports", nullable = false)
    private boolean inappReports;

    @Column(name = "email_messages", nullable = false)
    private boolean emailMessages;

    @Column(name = "inapp_messages", nullable = false)
    private boolean inappMessages;

    @Column(name = "email_system", nullable = false)
    private boolean emailSystem;

    @Column(name = "inapp_system", nullable = false)
    private boolean inappSystem;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean wantsInApp(NotificationType type) {
        return type.isInAppWantedBy(this);
    }

    public boolean wantsEmail(NotificationType type) {
        return emailEnabled && type.isEmailWantedBy(this);
    }

    public static NotificationPreference defaultsFor(String userSub) {
        return NotificationPreference.builder()
                .userSub(userSub)
                .emailEnabled(true)
                .emailPriceAlerts(true)
                .inappPriceAlerts(true)
                .emailWatchlist(false)
                .inappWatchlist(true)
                .emailReports(true)
                .inappReports(true)
                .emailMessages(false)
                .inappMessages(true)
                .emailSystem(false)
                .inappSystem(true)
                .build();
    }
}
