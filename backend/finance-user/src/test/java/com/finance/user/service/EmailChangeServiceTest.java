package com.finance.user.service;

import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.enums.ThemePreference;
import com.finance.user.model.EmailChangeRequest;
import com.finance.user.repository.EmailChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailChangeServiceTest {

    private static final String USER = "kc-user-1";
    private static final String OLD_EMAIL = "old@example.com";
    private static final String NEW_EMAIL = "new@example.com";
    private static final String VALID_CODE = "123456";

    @Mock private EmailChangeRequestRepository repository;
    @Mock private KeycloakAdminClient keycloakClient;
    @Mock private UserPreferenceService preferenceService;
    @Mock private EventPublisherPort eventPublisher;

    private EmailChangeService service;
    private UserSecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new UserSecurityProperties(
                new UserSecurityProperties.EmailChange(3, 6, Duration.ofMinutes(10)),
                new UserSecurityProperties.PasswordReset(300L),
                new UserSecurityProperties.Keycloak("finance-frontend", "themePreference", "locale"));
        service = new EmailChangeService(
                repository, keycloakClient, preferenceService, eventPublisher, securityProperties);
    }

    @Test
    void initiate_throwsResourceNotFound_whenCurrentEmailMissing() {
        when(keycloakClient.getEmail(USER)).thenReturn(null);

        assertThatThrownBy(() -> service.initiate(USER, NEW_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.email.currentNotFound");
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void initiate_throwsBadRequest_whenNewEmailMatchesCurrent() {
        when(keycloakClient.getEmail(USER)).thenReturn(OLD_EMAIL);

        assertThatThrownBy(() -> service.initiate(USER, OLD_EMAIL.toUpperCase()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.email.sameAddress");
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void initiate_createsNewRequestAndPublishesEvent_whenNoPriorRequest() {
        when(keycloakClient.getEmail(USER)).thenReturn(OLD_EMAIL);
        when(repository.findById(USER)).thenReturn(Optional.empty());
        when(preferenceService.getOrDefault(USER)).thenReturn(stubPreference());

        service.initiate(USER, NEW_EMAIL);

        ArgumentCaptor<EmailChangeRequest> captor = ArgumentCaptor.forClass(EmailChangeRequest.class);
        verify(repository).save(captor.capture());
        EmailChangeRequest saved = captor.getValue();
        assertThat(saved.getUserSub()).isEqualTo(USER);
        assertThat(saved.getNewEmail()).isEqualTo(NEW_EMAIL);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now());
        verify(eventPublisher).publish(any(EmailChangeCodeRequestedEvent.class));
    }

    @Test
    void initiate_resetsAttempts_whenTargetEmailChanged() {
        EmailChangeRequest existing = new EmailChangeRequest();
        existing.setUserSub(USER);
        existing.setNewEmail("different@example.com");
        existing.setAttempts(2);
        when(keycloakClient.getEmail(USER)).thenReturn(OLD_EMAIL);
        when(repository.findById(USER)).thenReturn(Optional.of(existing));
        when(preferenceService.getOrDefault(USER)).thenReturn(stubPreference());

        service.initiate(USER, NEW_EMAIL);

        assertThat(existing.getAttempts()).isZero();
        assertThat(existing.getNewEmail()).isEqualTo(NEW_EMAIL);
        verify(repository).save(existing);
    }

    @Test
    void initiate_keepsAttempts_whenTargetEmailUnchanged() {
        EmailChangeRequest existing = new EmailChangeRequest();
        existing.setUserSub(USER);
        existing.setNewEmail(NEW_EMAIL);
        existing.setAttempts(2);
        when(keycloakClient.getEmail(USER)).thenReturn(OLD_EMAIL);
        when(repository.findById(USER)).thenReturn(Optional.of(existing));
        when(preferenceService.getOrDefault(USER)).thenReturn(stubPreference());

        service.initiate(USER, NEW_EMAIL);

        assertThat(existing.getAttempts()).isEqualTo(2);
        verify(repository).save(existing);
    }

    @Test
    void confirm_throwsBadRequest_whenNoActiveChange() {
        when(repository.findById(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(USER, VALID_CODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.email.noActiveChange");
    }

    @Test
    void confirm_deletesRequestAndThrows_whenCodeExpired() {
        EmailChangeRequest request = activeRequest(VALID_CODE, 0, OffsetDateTime.now().minusMinutes(1));
        when(repository.findById(USER)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirm(USER, VALID_CODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.email.codeExpired");
        verify(repository).delete(request);
        verify(keycloakClient, never()).setEmail(any(), any());
    }

    @Test
    void confirm_deletesRequestAndThrows_whenTooManyAttempts() {
        EmailChangeRequest request = activeRequest(VALID_CODE, 3, OffsetDateTime.now().plusMinutes(5));
        when(repository.findById(USER)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirm(USER, VALID_CODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.email.tooManyAttempts");
        verify(repository).delete(request);
    }

    @Test
    void confirm_incrementsAttemptsAndThrows_whenCodeIsWrong() {
        EmailChangeRequest request = activeRequest(VALID_CODE, 0, OffsetDateTime.now().plusMinutes(5));
        when(repository.findById(USER)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirm(USER, "wrong-code"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.email.invalidCode");
        assertThat(request.getAttempts()).isEqualTo(1);
        verify(repository).save(request);
        verify(keycloakClient, never()).setEmail(any(), any());
    }

    @Test
    void confirm_setsEmailInKeycloakAndDeletesRequest_whenCodeIsValid() {
        EmailChangeRequest request = activeRequest(VALID_CODE, 0, OffsetDateTime.now().plusMinutes(5));
        when(repository.findById(USER)).thenReturn(Optional.of(request));

        service.confirm(USER, VALID_CODE);

        verify(keycloakClient).setEmail(USER, NEW_EMAIL);
        verify(repository).delete(request);
    }

    @Test
    void cancel_deletesRequest_whenPresent() {
        EmailChangeRequest request = new EmailChangeRequest();
        request.setUserSub(USER);
        when(repository.findById(USER)).thenReturn(Optional.of(request));

        service.cancel(USER);

        verify(repository).delete(request);
    }

    @Test
    void cancel_isNoOp_whenNotPresent() {
        when(repository.findById(USER)).thenReturn(Optional.empty());

        service.cancel(USER);

        verify(repository, never()).delete(any());
    }

    @Test
    void currentPending_returnsPendingState_whenRequestExists() {
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
        EmailChangeRequest request = new EmailChangeRequest();
        request.setUserSub(USER);
        request.setNewEmail(NEW_EMAIL);
        request.setExpiresAt(expiresAt);
        when(repository.findById(USER)).thenReturn(Optional.of(request));

        Optional<EmailChangeService.PendingState> pending = service.currentPending(USER);

        assertThat(pending).isPresent();
        assertThat(pending.get().newEmail()).isEqualTo(NEW_EMAIL);
        assertThat(pending.get().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void currentPending_returnsEmpty_whenNoRequest() {
        when(repository.findById(USER)).thenReturn(Optional.empty());

        Optional<EmailChangeService.PendingState> pending = service.currentPending(USER);

        assertThat(pending).isEmpty();
    }

    private EmailChangeRequest activeRequest(String code, int attempts, OffsetDateTime expiresAt) {
        EmailChangeRequest r = new EmailChangeRequest();
        r.setUserSub(USER);
        r.setNewEmail(NEW_EMAIL);
        r.setCodeHash(new BCryptPasswordEncoder().encode(code));
        r.setAttempts(attempts);
        r.setExpiresAt(expiresAt);
        return r;
    }

    private UserPreferenceResponse stubPreference() {
        return new UserPreferenceResponse(USER, ThemePreference.DARK, "tr",
                "Europe/Istanbul", "1M", true);
    }
}
