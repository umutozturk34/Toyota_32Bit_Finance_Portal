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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for the current user's security credentials: password change (via Keycloak action email),
 * the code-verified email-change flow (initiate/confirm/pending/cancel), and two-factor management
 * (status, disable, list/remove devices). All endpoints are authenticated and scoped to the JWT subject.
 */
@RestController
@RequestMapping("/api/v1/user/credentials")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserCredentialService credentialService;
    private final EmailChangeService emailChangeService;
    private final TwoFactorService twoFactorService;
    private final Translator translator;

    /** Triggers Keycloak's password-reset action email; {@code redirectUri} is where the user lands after completing it. */
    @PostMapping("/password/initiate-change")
    public ApiResponse<Void> initiatePasswordChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeInitiateRequest request) {
        credentialService.initiatePasswordChange(jwt.getSubject(), request.redirectUri());
        return ApiResponse.success(translator.translate("api.credential.passwordResetSent"), null);
    }

    /** Starts an email change by emailing a verification code to {@code newEmail}; the change is not applied until confirmed. */
    @PostMapping("/email/initiate-change")
    public ApiResponse<Void> initiateEmailChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeInitiateRequest request) {
        emailChangeService.initiate(jwt.getSubject(), request.newEmail());
        return ApiResponse.success(translator.translate("api.credential.emailCodeSent"), null);
    }

    /** Confirms a pending email change with the emailed {@code code}, committing the new address. */
    @PostMapping("/email/confirm-change")
    public ApiResponse<Void> confirmEmailChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeConfirmRequest request) {
        emailChangeService.confirm(jwt.getSubject(), request.code());
        return ApiResponse.success(translator.translate("api.credential.emailUpdated"), null);
    }

    /** The pending email change (target address and expiry), or {@code null} if none is in progress. */
    @GetMapping("/email/pending")
    public ApiResponse<EmailChangePendingResponse> getPendingEmailChange(@AuthenticationPrincipal Jwt jwt) {
        EmailChangePendingResponse pending = emailChangeService.currentPending(jwt.getSubject())
                .map(p -> new EmailChangePendingResponse(p.newEmail(), p.expiresAt()))
                .orElse(null);
        return ApiResponse.success(translator.translate("api.credential.pendingEmailChange"), pending);
    }

    /** Cancels any in-progress email change. */
    @DeleteMapping("/email/pending")
    public ApiResponse<Void> cancelEmailChange(@AuthenticationPrincipal Jwt jwt) {
        emailChangeService.cancel(jwt.getSubject());
        return ApiResponse.success(translator.translate("api.credential.emailChangeCancelled"), null);
    }

    /** Current two-factor status (whether enabled and how many devices are registered). */
    @GetMapping("/2fa")
    public ApiResponse<TwoFactorService.TwoFactorStatus> getTwoFactorStatus(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.credential.twoFactorStatus"), twoFactorService.status(jwt.getSubject()));
    }

    /** Disables two-factor by removing all OTP devices; returns the number removed. */
    @DeleteMapping("/2fa")
    public ApiResponse<Integer> disableTwoFactor(@AuthenticationPrincipal Jwt jwt) {
        int removed = twoFactorService.disable(jwt.getSubject());
        return ApiResponse.success(translator.translate("api.credential.twoFactorDisabled"), removed);
    }

    /** Lists the user's registered two-factor (OTP) devices. */
    @GetMapping("/2fa/devices")
    public ApiResponse<List<TwoFactorService.TwoFactorDevice>> listTwoFactorDevices(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.credential.twoFactorDevicesListed"),
                twoFactorService.devices(jwt.getSubject()));
    }

    /** Removes a single two-factor device by its Keycloak {@code credentialId}. */
    @DeleteMapping("/2fa/devices/{credentialId}")
    public ApiResponse<Void> removeTwoFactorDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String credentialId) {
        twoFactorService.removeDevice(jwt.getSubject(), credentialId);
        return ApiResponse.success(translator.translate("api.credential.twoFactorDeviceRemoved"), null);
    }
}
