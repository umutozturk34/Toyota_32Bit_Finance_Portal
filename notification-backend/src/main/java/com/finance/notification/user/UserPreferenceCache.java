package com.finance.notification.user;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_preferences_cache")
public class UserPreferenceCache {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Column(name = "theme", length = 16)
    private String theme;

    @Column(name = "language", length = 8)
    private String language;

    @Column(name = "timezone", length = 32)
    private String timezone;

    @Column(name = "default_chart_range", length = 8)
    private String defaultChartRange;

    @Column(name = "report_frequency", length = 16)
    private String reportFrequency;

    @Column(name = "onboarding_completed")
    private Boolean onboardingCompleted;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    public static UserPreferenceCache fromEvent(UserPreferencesUpdatedEvent event) {
        return UserPreferenceCache.builder()
                .userSub(event.userSub())
                .theme(event.theme())
                .language(event.language())
                .timezone(event.timezone())
                .defaultChartRange(event.defaultChartRange())
                .reportFrequency(event.reportFrequency())
                .onboardingCompleted(event.onboardingCompleted())
                .syncedAt(LocalDateTime.now())
                .build();
    }

    public void applyEvent(UserPreferencesUpdatedEvent event) {
        this.theme = event.theme();
        this.language = event.language();
        this.timezone = event.timezone();
        this.defaultChartRange = event.defaultChartRange();
        this.reportFrequency = event.reportFrequency();
        this.onboardingCompleted = event.onboardingCompleted();
        this.syncedAt = LocalDateTime.now();
    }

    public boolean wantsDailyReport() {
        return "DAILY".equals(reportFrequency);
    }

    public boolean wantsWeeklyReport() {
        return "WEEKLY".equals(reportFrequency);
    }

    public boolean wantsMonthlyReport() {
        return "MONTHLY".equals(reportFrequency);
    }
}
