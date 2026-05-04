package com.finance.notification.user;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserPreferenceCacheTest {

    private static final String USER_SUB = "kc-user-sub";

    @Test
    void should_buildCacheFromEvent_when_usingFromEventFactory() {
        UserPreferencesUpdatedEvent event = new UserPreferencesUpdatedEvent(
                UUID.randomUUID().toString(), USER_SUB, OffsetDateTime.now(),
                "DARK", "tr", "Europe/Istanbul", "1M", "DAILY", true);

        UserPreferenceCache cache = UserPreferenceCache.fromEvent(event);

        assertThat(cache.getUserSub()).isEqualTo(USER_SUB);
        assertThat(cache.getTheme()).isEqualTo("DARK");
        assertThat(cache.getLanguage()).isEqualTo("tr");
        assertThat(cache.getReportFrequency()).isEqualTo("DAILY");
        assertThat(cache.getOnboardingCompleted()).isTrue();
        assertThat(cache.getSyncedAt()).isNotNull();
    }

    @Test
    void should_overwriteFieldsAndUpdateSyncedAt_when_applyingNewerEvent() {
        UserPreferenceCache cache = UserPreferenceCache.builder()
                .userSub(USER_SUB)
                .theme("LIGHT").language("en")
                .reportFrequency("NEVER")
                .onboardingCompleted(false)
                .syncedAt(java.time.LocalDateTime.now().minusDays(1))
                .build();
        java.time.LocalDateTime previousSync = cache.getSyncedAt();
        UserPreferencesUpdatedEvent event = new UserPreferencesUpdatedEvent(
                UUID.randomUUID().toString(), USER_SUB, OffsetDateTime.now(),
                "DARK", "tr", "UTC", "1Y", "WEEKLY", true);

        cache.applyEvent(event);

        assertThat(cache.getTheme()).isEqualTo("DARK");
        assertThat(cache.getLanguage()).isEqualTo("tr");
        assertThat(cache.getTimezone()).isEqualTo("UTC");
        assertThat(cache.getReportFrequency()).isEqualTo("WEEKLY");
        assertThat(cache.getOnboardingCompleted()).isTrue();
        assertThat(cache.getSyncedAt()).isAfter(previousSync);
    }

    @Test
    void should_returnTrueForDailyAndFalseForOthers_when_dailyReportRequested() {
        UserPreferenceCache cache = UserPreferenceCache.builder()
                .reportFrequency("DAILY").build();

        assertThat(cache.wantsDailyReport()).isTrue();
        assertThat(cache.wantsWeeklyReport()).isFalse();
        assertThat(cache.wantsMonthlyReport()).isFalse();
    }

    @Test
    void should_returnFalseForAllReportChecks_when_frequencyIsNullOrNever() {
        UserPreferenceCache empty = UserPreferenceCache.builder().build();
        UserPreferenceCache never = UserPreferenceCache.builder().reportFrequency("NEVER").build();

        assertThat(empty.wantsDailyReport()).isFalse();
        assertThat(empty.wantsWeeklyReport()).isFalse();
        assertThat(empty.wantsMonthlyReport()).isFalse();
        assertThat(never.wantsDailyReport()).isFalse();
    }
}
