package com.finance.user.model;

import com.finance.user.dto.enums.ThemePreference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-user application preferences keyed by Keycloak subject: theme, language, timezone, default
 * chart range, and the onboarding-completed flag. One row per user; {@code onboardingCompleted}
 * tracks whether the first-run onboarding flow has been finished.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "user_preferences")
@org.hibernate.annotations.DynamicUpdate
public class UserPreference {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "user_sub", nullable = false, length = 64)
    private String userSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 16)
    private ThemePreference theme;

    @Column(name = "language", nullable = false, length = 8)
    private String language;

    @Column(name = "timezone", nullable = false, length = 32)
    private String timezone;

    @Column(name = "default_chart_range", nullable = false, length = 8)
    private String defaultChartRange;

    @Column(name = "onboarding_completed", nullable = false)
    private Boolean onboardingCompleted;

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

    /** Builds the default preference row for a new user: dark theme, Turkish, Europe/Istanbul, 1M range, onboarding not yet completed. */
    public static UserPreference defaultsFor(String userSub) {
        return UserPreference.builder()
                .userSub(userSub)
                .theme(ThemePreference.DARK)
                .language("tr")
                .timezone("Europe/Istanbul")
                .defaultChartRange("1M")
                .onboardingCompleted(false)
                .build();
    }
}
