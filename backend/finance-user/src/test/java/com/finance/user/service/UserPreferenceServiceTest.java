package com.finance.user.service;


import com.finance.common.event.UserRegisteredEvent;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
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
    void should_publishUserRegisteredEvent_when_newPreferenceRowIsCreated() {
        // Arrange
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        service.getOrDefault(USER_SUB);

        // Assert
        org.mockito.ArgumentCaptor<UserRegisteredEvent> captor =
                org.mockito.ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publish(captor.capture());
        UserRegisteredEvent event = captor.getValue();
        assertThat(event.userSub()).isEqualTo(USER_SUB);
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void should_notPublishEvent_when_existingPreferenceFound() {
        // Arrange
        UserPreference persisted = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.DARK)
                .language("tr")
                .timezone("UTC")
                .defaultChartRange("1Y")
                .onboardingCompleted(true)
                .build();
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(persisted));

        // Act
        service.getOrDefault(USER_SUB);

        // Assert
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void should_swallowKeycloakHydrationException_when_attributeLookupFails() {
        // Arrange
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(keycloakAdminClient.getUserAttribute(eq(USER_SUB), any()))
                .thenThrow(new RuntimeException("kc down"));
        when(repository.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        // Assert
        assertThat(response.theme()).isEqualTo(ThemePreference.DARK);
        assertThat(response.language()).isEqualTo("tr");
        verify(repository).save(any(UserPreference.class));
    }

    @ParameterizedTest
    @CsvSource({
            "not-a-theme",
            "PURPLE",
            "''"
    })
    void should_keepDefaultTheme_when_keycloakThemeAttributeIsInvalid(String rawTheme) {
        // Arrange
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(keycloakAdminClient.getUserAttribute(USER_SUB, "themePreference"))
                .thenReturn(Optional.of(rawTheme));
        when(keycloakAdminClient.getUserAttribute(USER_SUB, "locale"))
                .thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        // Assert
        assertThat(response.theme()).isEqualTo(ThemePreference.DARK);
    }

    @ParameterizedTest
    @CsvSource({
            "fr",
            "de",
            "xx"
    })
    void should_keepDefaultLanguage_when_keycloakLocaleNotInSupportedSet(String rawLocale) {
        // Arrange
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(keycloakAdminClient.getUserAttribute(USER_SUB, "themePreference"))
                .thenReturn(Optional.empty());
        when(keycloakAdminClient.getUserAttribute(USER_SUB, "locale"))
                .thenReturn(Optional.of(rawLocale));
        when(repository.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        UserPreferenceResponse response = service.getOrDefault(USER_SUB);

        // Assert
        assertThat(response.language()).isEqualTo("tr");
    }

    @Test
    void should_returnEmptyOptional_when_findPersistedHasNoRow() {
        // Arrange
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());

        // Act
        Optional<UserPreferenceResponse> result = service.findPersisted(USER_SUB);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void should_returnMappedResponse_when_findPersistedHasRow() {
        // Arrange
        UserPreference persisted = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.LIGHT)
                .language("en")
                .timezone("UTC")
                .defaultChartRange("3M")
                .onboardingCompleted(false)
                .build();
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(persisted));

        // Act
        Optional<UserPreferenceResponse> result = service.findPersisted(USER_SUB);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().theme()).isEqualTo(ThemePreference.LIGHT);
        assertThat(result.get().language()).isEqualTo("en");
    }

    @Test
    void should_throwBusinessException_when_keycloakAttributeSyncFails() {
        // Arrange
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                null, "en", null, null, null);
        doThrow(new RuntimeException("kc 5xx"))
                .when(keycloakAdminClient).setUserAttribute(USER_SUB, "locale", "en");

        // Act / Assert
        assertThatThrownBy(() -> service.upsert(USER_SUB, request))
                .isInstanceOf(com.finance.common.exception.BusinessException.class)
                .hasMessageContaining("error.preferences.syncFailed");
        verify(repository, never()).save(any(UserPreference.class));
    }

    @Test
    void should_updateTimezone_when_timezoneProvidedInUpsert() {
        // Arrange
        UserPreference existing = UserPreference.builder()
                .userSub(USER_SUB)
                .theme(ThemePreference.DARK)
                .language("tr")
                .timezone("UTC")
                .defaultChartRange("3M")
                .onboardingCompleted(false)
                .build();
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                null, null, "Europe/Istanbul", null, null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.upsert(USER_SUB, request);

        // Assert
        assertThat(existing.getTimezone()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void should_syncThemeToKeycloak_when_themeProvidedInUpsert() {
        // Arrange
        UserPreferenceUpdateRequest request = new UserPreferenceUpdateRequest(
                ThemePreference.LIGHT, null, null, null, null);
        when(repository.findById(USER_SUB)).thenReturn(Optional.empty());
        when(repository.save(any(UserPreference.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        service.upsert(USER_SUB, request);

        // Assert
        verify(keycloakAdminClient).setUserAttribute(USER_SUB, "themePreference", "LIGHT");
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
