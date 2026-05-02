package com.finance.backend.service;

import com.finance.backend.dto.UserPreferenceResponse;
import com.finance.backend.dto.UserPreferenceUpdateRequest;
import com.finance.backend.dto.enums.ReportFrequency;
import com.finance.backend.dto.enums.ThemePreference;
import com.finance.backend.mapper.UserPreferenceMapper;
import com.finance.backend.mapper.UserPreferenceMapperImpl;
import com.finance.backend.model.UserPreference;
import com.finance.backend.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    private static final String USER_SUB = "kc-user-uuid-123";

    @Mock private UserPreferenceRepository repository;

    private UserPreferenceMapper mapper;
    private UserPreferenceService service;

    @BeforeEach
    void setUp() {
        mapper = new UserPreferenceMapperImpl();
        service = new UserPreferenceService(repository, mapper);
    }

    @Test
    void shouldReturnTransientDefaults_whenNoPreferenceExists() {
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());

        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        assertThat(response.userSub()).isEqualTo(USER_SUB);
        assertThat(response.theme()).isEqualTo(ThemePreference.SYSTEM);
        assertThat(response.language()).isEqualTo("tr");
        assertThat(response.timezone()).isEqualTo("Europe/Istanbul");
        assertThat(response.defaultChartRange()).isEqualTo("6M");
        assertThat(response.reportFrequency()).isEqualTo(ReportFrequency.NEVER);
        assertThat(response.onboardingCompleted()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void shouldReturnPersistedPreference_whenFound() {
        UserPreference persisted = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.DARK)
                .language("en")
                .timezone("UTC")
                .defaultChartRange("1Y")
                .reportFrequency(ReportFrequency.WEEKLY)
                .onboardingCompleted(true)
                .build();
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(persisted));

        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        assertThat(response.theme()).isEqualTo(ThemePreference.DARK);
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.defaultChartRange()).isEqualTo("1Y");
        assertThat(response.reportFrequency()).isEqualTo(ReportFrequency.WEEKLY);
        assertThat(response.onboardingCompleted()).isTrue();
    }

    @Test
    void shouldCreateNewRowFromDefaults_whenUpsertOnEmptyDb() {
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                ThemePreference.DARK, null, null, null, null, true);
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferenceResponse response = service.upsert(USER_SUB, request);

        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(repository).save(captor.capture());
        UserPreference saved = captor.getValue();
        assertThat(saved.getUserSub()).isEqualTo(USER_SUB);
        assertThat(saved.getTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(saved.getLanguage()).isEqualTo("tr");
        assertThat(saved.getOnboardingCompleted()).isTrue();
        assertThat(response.theme()).isEqualTo(ThemePreference.DARK);
    }

    @Test
    void shouldMergeOnlyProvidedFields_whenUpsertingExisting() {
        UserPreference existing = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.LIGHT)
                .language("tr")
                .timezone("Europe/Istanbul")
                .defaultChartRange("6M")
                .reportFrequency(ReportFrequency.NEVER)
                .onboardingCompleted(false)
                .build();
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                null, "en", null, "1Y", null, null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(USER_SUB, request);

        assertThat(existing.getTheme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(existing.getLanguage()).isEqualTo("en");
        assertThat(existing.getDefaultChartRange()).isEqualTo("1Y");
        assertThat(existing.getReportFrequency()).isEqualTo(ReportFrequency.NEVER);
        assertThat(existing.getOnboardingCompleted()).isFalse();
    }

    @Test
    void shouldIgnoreAllNullFields_whenUpsertWithEmptyRequest() {
        UserPreference existing = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.DARK)
                .language("en")
                .timezone("UTC")
                .defaultChartRange("3M")
                .reportFrequency(ReportFrequency.DAILY)
                .onboardingCompleted(true)
                .build();
        UserPreferenceUpdateRequest empty = new UserPreferenceUpdateRequest(null, null, null, null, null, null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(USER_SUB, empty);

        assertThat(existing.getTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(existing.getLanguage()).isEqualTo("en");
        assertThat(existing.getDefaultChartRange()).isEqualTo("3M");
        assertThat(existing.getReportFrequency()).isEqualTo(ReportFrequency.DAILY);
        assertThat(existing.getOnboardingCompleted()).isTrue();
    }
}
