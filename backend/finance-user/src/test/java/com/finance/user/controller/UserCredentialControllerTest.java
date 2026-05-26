package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.EmailChangeConfirmRequest;
import com.finance.user.dto.EmailChangeInitiateRequest;
import com.finance.user.dto.EmailChangePendingResponse;
import com.finance.user.dto.PasswordChangeInitiateRequest;
import com.finance.user.service.EmailChangeService;
import com.finance.user.service.TwoFactorService;
import com.finance.user.service.UserCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserCredentialControllerTest {

    private static final String USER = "kc-user-1";

    @Mock private UserCredentialService credentialService;
    @Mock private EmailChangeService emailChangeService;
    @Mock private TwoFactorService twoFactorService;
    @Mock private Translator translator;

    private UserCredentialController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller = new UserCredentialController(credentialService, emailChangeService, twoFactorService, translator);
        jwt = Jwt.withTokenValue("t").header("alg", "none").subject(USER).build();
    }

    @Test
    void initiatePasswordChange_delegatesToCredentialService() {
        PasswordChangeInitiateRequest request = new PasswordChangeInitiateRequest("https://app/x");
        when(translator.translate("api.credential.passwordResetSent")).thenReturn("sent");

        ApiResponse<Void> response = controller.initiatePasswordChange(jwt, request);

        assertThat(response.getMessage()).isEqualTo("sent");
        verify(credentialService).initiatePasswordChange(USER, "https://app/x");
    }

    @Test
    void initiateEmailChange_delegatesToEmailService() {
        EmailChangeInitiateRequest request = new EmailChangeInitiateRequest("new@x.com");
        when(translator.translate("api.credential.emailCodeSent")).thenReturn("sent");

        ApiResponse<Void> response = controller.initiateEmailChange(jwt, request);

        assertThat(response.getMessage()).isEqualTo("sent");
        verify(emailChangeService).initiate(USER, "new@x.com");
    }

    @Test
    void confirmEmailChange_delegatesToEmailService() {
        EmailChangeConfirmRequest request = new EmailChangeConfirmRequest("123456");
        when(translator.translate("api.credential.emailUpdated")).thenReturn("updated");

        ApiResponse<Void> response = controller.confirmEmailChange(jwt, request);

        assertThat(response.getMessage()).isEqualTo("updated");
        verify(emailChangeService).confirm(USER, "123456");
    }

    @Test
    void getPendingEmailChange_returnsPendingResponse_whenChangePresent() {
        OffsetDateTime expiresAt = OffsetDateTime.now();
        when(emailChangeService.currentPending(USER))
                .thenReturn(Optional.of(new EmailChangeService.PendingState("new@x.com", expiresAt)));
        when(translator.translate("api.credential.pendingEmailChange")).thenReturn("pending");

        ApiResponse<EmailChangePendingResponse> response = controller.getPendingEmailChange(jwt);

        assertThat(response.getMessage()).isEqualTo("pending");
        assertThat(response.getData().newEmail()).isEqualTo("new@x.com");
        assertThat(response.getData().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void getPendingEmailChange_returnsNullData_whenNoPending() {
        when(emailChangeService.currentPending(USER)).thenReturn(Optional.empty());
        when(translator.translate("api.credential.pendingEmailChange")).thenReturn("pending");

        ApiResponse<EmailChangePendingResponse> response = controller.getPendingEmailChange(jwt);

        assertThat(response.getData()).isNull();
    }

    @Test
    void cancelEmailChange_delegatesToEmailService() {
        when(translator.translate("api.credential.emailChangeCancelled")).thenReturn("cancelled");

        ApiResponse<Void> response = controller.cancelEmailChange(jwt);

        assertThat(response.getMessage()).isEqualTo("cancelled");
        verify(emailChangeService).cancel(USER);
    }

    @Test
    void getTwoFactorStatus_delegatesToService() {
        TwoFactorService.TwoFactorStatus status = new TwoFactorService.TwoFactorStatus(true);
        when(twoFactorService.status(USER)).thenReturn(status);
        when(translator.translate("api.credential.twoFactorStatus")).thenReturn("status");

        ApiResponse<TwoFactorService.TwoFactorStatus> response = controller.getTwoFactorStatus(jwt);

        assertThat(response.getMessage()).isEqualTo("status");
        assertThat(response.getData()).isSameAs(status);
    }

    @Test
    void disableTwoFactor_returnsRemovedCount() {
        when(twoFactorService.disable(USER)).thenReturn(2);
        when(translator.translate("api.credential.twoFactorDisabled")).thenReturn("disabled");

        ApiResponse<Integer> response = controller.disableTwoFactor(jwt);

        assertThat(response.getMessage()).isEqualTo("disabled");
        assertThat(response.getData()).isEqualTo(2);
    }

    @Test
    void listTwoFactorDevices_returnsDeviceList() {
        java.util.List<com.finance.user.service.TwoFactorService.TwoFactorDevice> devices =
                java.util.List.of(new com.finance.user.service.TwoFactorService.TwoFactorDevice(
                        "c-1", "Authenticator", 1700000000000L));
        when(twoFactorService.devices(USER)).thenReturn(devices);
        when(translator.translate("api.credential.twoFactorDevicesListed")).thenReturn("listed");

        ApiResponse<java.util.List<com.finance.user.service.TwoFactorService.TwoFactorDevice>> response =
                controller.listTwoFactorDevices(jwt);

        assertThat(response.getMessage()).isEqualTo("listed");
        assertThat(response.getData()).isSameAs(devices);
    }

    @Test
    void removeTwoFactorDevice_delegatesToServiceAndReturnsMessage() {
        when(translator.translate("api.credential.twoFactorDeviceRemoved")).thenReturn("removed");

        ApiResponse<Void> response = controller.removeTwoFactorDevice(jwt, "cred-99");

        assertThat(response.getMessage()).isEqualTo("removed");
        verify(twoFactorService).removeDevice(USER, "cred-99");
    }
}
