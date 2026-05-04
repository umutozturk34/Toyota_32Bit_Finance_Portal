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
import java.time.LocalTime;
import java.time.ZoneId;

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

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

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
        return switch (type) {
            case PRICE_ALERT_FIRED -> inappPriceAlerts;
            case WATCHLIST_DELTA -> inappWatchlist;
            case REPORT_READY -> inappReports;
            case MESSAGE -> inappMessages;
            case SYSTEM -> inappSystem;
        };
    }

    public boolean wantsEmail(NotificationType type) {
        return switch (type) {
            case PRICE_ALERT_FIRED -> emailPriceAlerts;
            case WATCHLIST_DELTA -> emailWatchlist;
            case REPORT_READY -> emailReports;
            case MESSAGE -> emailMessages;
            case SYSTEM -> emailSystem;
        };
    }

    public boolean isInQuietHours(ZoneId userZone) {
        return isInQuietHours(LocalTime.now(userZone));
    }

    public boolean isInQuietHours(LocalTime now) {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return !now.isBefore(quietHoursStart) && now.isBefore(quietHoursEnd);
        }
        return !now.isBefore(quietHoursStart) || now.isBefore(quietHoursEnd);
    }

    public static NotificationPreference defaultsFor(String userSub) {
        return NotificationPreference.builder()
                .userSub(userSub)
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
