package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    private static final String USER = "kc-user-1";

    @Mock private KeycloakAdminClient client;

    private TwoFactorService service;

    @BeforeEach
    void setUp() {
        service = new TwoFactorService(client);
    }

    @Test
    void status_returnsConfiguredTrue_whenOtpCredentialPresent() {
        when(client.listCredentials(USER)).thenReturn(List.of(
                Map.of("type", "otp", "id", "cred-1")
        ));

        TwoFactorService.TwoFactorStatus result = service.status(USER);

        assertThat(result.configured()).isTrue();
    }

    @Test
    void status_returnsConfiguredFalse_whenNoCredentials() {
        when(client.listCredentials(USER)).thenReturn(List.of());

        TwoFactorService.TwoFactorStatus result = service.status(USER);

        assertThat(result.configured()).isFalse();
    }

    @Test
    void status_returnsConfiguredFalse_whenOnlyNonOtpCredentials() {
        when(client.listCredentials(USER)).thenReturn(List.of(
                Map.of("type", "password", "id", "pw-1"),
                Map.of("type", "webauthn", "id", "wa-1")
        ));

        TwoFactorService.TwoFactorStatus result = service.status(USER);

        assertThat(result.configured()).isFalse();
    }

    @Test
    void disable_deletesOnlyOtpCredentialsAndReturnsCount() {
        when(client.listCredentials(USER)).thenReturn(List.of(
                Map.of("type", "otp", "id", "otp-1"),
                Map.of("type", "password", "id", "pw-1"),
                Map.of("type", "otp", "id", "otp-2")
        ));

        int removed = service.disable(USER);

        assertThat(removed).isEqualTo(2);
        verify(client).deleteCredential(USER, "otp-1");
        verify(client).deleteCredential(USER, "otp-2");
        verify(client, never()).deleteCredential(USER, "pw-1");
    }

    @Test
    void disable_returnsZeroAndDeletesNothing_whenNoOtpCredentials() {
        when(client.listCredentials(USER)).thenReturn(List.of(
                Map.of("type", "password", "id", "pw-1")
        ));

        int removed = service.disable(USER);

        assertThat(removed).isZero();
        verify(client, never()).deleteCredential(USER, "pw-1");
    }

    @Test
    void disable_skipsOtpCredentialsMissingId() {
        when(client.listCredentials(USER)).thenReturn(List.of(
                Map.of("type", "otp", "label", "missing-id")
        ));

        int removed = service.disable(USER);

        assertThat(removed).isZero();
        verify(client, times(1)).listCredentials(USER);
        verify(client, never()).deleteCredential(USER, null);
    }
}
