package com.finance.notification.user;

import com.finance.common.event.UserPreferencesUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceCacheServiceTest {

    private static final String USER_SUB = "kc-user-sub";

    @Mock private UserPreferenceCacheRepository repository;

    private UserPreferenceCacheService service;

    @BeforeEach
    void setUp() {
        service = new UserPreferenceCacheService(repository);
    }

    @Test
    void should_insertNewCacheRow_when_userHasNoExistingCache() {
        UserPreferencesUpdatedEvent event = new UserPreferencesUpdatedEvent(
                UUID.randomUUID().toString(), USER_SUB, OffsetDateTime.now(),
                "DARK", "tr", "UTC", "1M", "DAILY", true);
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());

        service.upsertFromEvent(event);

        ArgumentCaptor<UserPreferenceCache> captor = ArgumentCaptor.forClass(UserPreferenceCache.class);
        verify(repository).save(captor.capture());
        UserPreferenceCache saved = captor.getValue();
        assertThat(saved.getUserSub()).isEqualTo(USER_SUB);
        assertThat(saved.getTheme()).isEqualTo("DARK");
        assertThat(saved.getReportFrequency()).isEqualTo("DAILY");
    }

    @Test
    void should_overwriteExistingCache_when_userAlreadyHasOne() {
        UserPreferenceCache existing = UserPreferenceCache.builder()
                .userSub(USER_SUB).theme("LIGHT").language("en")
                .reportFrequency("NEVER").syncedAt(java.time.LocalDateTime.now().minusDays(1))
                .build();
        UserPreferencesUpdatedEvent event = new UserPreferencesUpdatedEvent(
                UUID.randomUUID().toString(), USER_SUB, OffsetDateTime.now(),
                "DARK", "tr", "UTC", "1Y", "WEEKLY", false);
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));

        service.upsertFromEvent(event);

        verify(repository).save(existing);
        assertThat(existing.getTheme()).isEqualTo("DARK");
        assertThat(existing.getLanguage()).isEqualTo("tr");
        assertThat(existing.getReportFrequency()).isEqualTo("WEEKLY");
    }
}
