package com.finance.user.service;


import com.finance.shared.event.EventPublisherPort;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.UserPreferenceUpdateRequest;
import com.finance.user.dto.enums.ThemePreference;
import com.finance.user.mapper.UserPreferenceMapper;
import com.finance.user.mapper.UserPreferenceMapperImpl;
import com.finance.user.model.UserPreference;
import com.finance.user.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    private static final String USER_SUB = "kc-user-uuid-123";

    @Mock private UserPreferenceRepository repository;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private EventPublisherPort eventPublisher;

    private UserPreferenceMapper mapper;
    private UserSecurityProperties securityProperties;
    private UserPreferenceService service;

    @BeforeEach
    void setUp() {
        mapper = new UserPreferenceMapperImpl();
        securityProperties = new UserSecurityProperties(
                new UserSecurityProperties.EmailChange(5, 6, Duration.ofMinutes(5)),
                new UserSecurityProperties.PasswordReset(300L),
                new UserSecurityProperties.Keycloak("finance-frontend", "themePreference", "locale"));
        service = new UserPreferenceService(repository, mapper, keycloakAdminClient, securityProperties, eventPublisher);
    }

    @Test
    void shouldPersistKeycloakAttributes_whenNoPreferenceExists() {
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(keycloakAdminClient.getUserAttribute(USER_SUB, "themePreference"))
                .thenReturn(Optional.of("LIGHT"));
        when(keycloakAdminClient.getUserAttribute(USER_SUB, "locale"))
                .thenReturn(Optional.of("en"));
        when(repository.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));

        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        assertThat(response.userSub()).isEqualTo(USER_SUB);
        assertThat(response.theme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(response.language()).isEqualTo("en");
        verify(repository).save(any(UserPreference.class));
    }

    @Test
    void shouldFallBackToDefaults_whenKeycloakAttributesMissing() {
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(keycloakAdminClient.getUserAttribute(eq(USER_SUB), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));

        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        assertThat(response.theme()).isEqualTo(ThemePreference.DARK);
        assertThat(response.language()).isEqualTo("tr");
        verify(repository).save(any(UserPreference.class));
    }

    @Test
    void shouldReturnPersistedPreference_whenFound() {
        UserPreference persisted = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.DARK)
                .language("en")
                .timezone("UTC")
                .defaultChartRange("1Y")
                .onboardingCompleted(true)
                .build();
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(persisted));

        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        assertThat(response.theme()).isEqualTo(ThemePreference.DARK);
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.defaultChartRange()).isEqualTo("1Y");
        assertThat(response.onboardingCompleted()).isTrue();
    }

    @Test
    void shouldCreateNewRowFromDefaults_whenUpsertOnEmptyDb() {
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                ThemePreference.DARK, null, null, null, true);
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferenceResponse response = service.upsert(USER_SUB, request);

        org.mockito.ArgumentCaptor<UserPreference> captor = org.mockito.ArgumentCaptor.forClass(UserPreference.class);
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
                .onboardingCompleted(false)
                .build();
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                null, "en", null, "1Y", null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(USER_SUB, request);

        assertThat(existing.getTheme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(existing.getLanguage()).isEqualTo("en");
        assertThat(existing.getDefaultChartRange()).isEqualTo("1Y");
        assertThat(existing.getOnboardingCompleted()).isFalse();
    }

    @Test
    void should_syncLocaleToKeycloak_when_languageProvided() {
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                null, "en", null, null, null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(USER_SUB, request);

        verify(keycloakAdminClient).setUserAttribute(USER_SUB, "locale", "en");
    }

    @Test
    void should_skipKeycloakSync_when_neitherLanguageNorThemeProvided() {
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                null, null, null, null, null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(USER_SUB, request);

        verify(keycloakAdminClient, never()).setUserAttribute(any(), any(), any());
    }

    @Test
    void shouldIgnoreAllNullFields_whenUpsertWithEmptyRequest() {
        UserPreference existing = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.DARK)
                .language("en")
                .timezone("UTC")
                .defaultChartRange("3M")
                .onboardingCompleted(true)
                .build();
        UserPreferenceUpdateRequest empty = new UserPreferenceUpdateRequest(null, null, null, null, null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(USER_SUB, empty);

        assertThat(existing.getTheme()).isEqualTo(ThemePreference.DARK);
        assertThat(existing.getLanguage()).isEqualTo("en");
        assertThat(existing.getDefaultChartRange()).isEqualTo("3M");
        assertThat(existing.getOnboardingCompleted()).isTrue();
    }
}
