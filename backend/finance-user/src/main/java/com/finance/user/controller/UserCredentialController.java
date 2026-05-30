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

    @PostMapping("/password/initiate-change")
    public ApiResponse<Void> initiatePasswordChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeInitiateRequest request) {
        credentialService.initiatePasswordChange(jwt.getSubject(), request.redirectUri());
        return ApiResponse.success(translator.translate("api.credential.passwordResetSent"), null);
    }

    @PostMapping("/email/initiate-change")
    public ApiResponse<Void> initiateEmailChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeInitiateRequest request) {
        emailChangeService.initiate(jwt.getSubject(), request.newEmail());
        return ApiResponse.success(translator.translate("api.credential.emailCodeSent"), null);
    }

    @PostMapping("/email/confirm-change")
    public ApiResponse<Void> confirmEmailChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeConfirmRequest request) {
        emailChangeService.confirm(jwt.getSubject(), request.code());
        return ApiResponse.success(translator.translate("api.credential.emailUpdated"), null);
    }

    @GetMapping("/email/pending")
    public ApiResponse<EmailChangePendingResponse> getPendingEmailChange(@AuthenticationPrincipal Jwt jwt) {
        EmailChangePendingResponse pending = emailChangeService.currentPending(jwt.getSubject())
                .map(p -> new EmailChangePendingResponse(p.newEmail(), p.expiresAt()))
                .orElse(null);
        return ApiResponse.success(translator.translate("api.credential.pendingEmailChange"), pending);
    }

    @DeleteMapping("/email/pending")
    public ApiResponse<Void> cancelEmailChange(@AuthenticationPrincipal Jwt jwt) {
        emailChangeService.cancel(jwt.getSubject());
        return ApiResponse.success(translator.translate("api.credential.emailChangeCancelled"), null);
    }

    @GetMapping("/2fa")
    public ApiResponse<TwoFactorService.TwoFactorStatus> getTwoFactorStatus(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.credential.twoFactorStatus"), twoFactorService.status(jwt.getSubject()));
    }

    @DeleteMapping("/2fa")
    public ApiResponse<Integer> disableTwoFactor(@AuthenticationPrincipal Jwt jwt) {
        int removed = twoFactorService.disable(jwt.getSubject());
        return ApiResponse.success(translator.translate("api.credential.twoFactorDisabled"), removed);
    }

    @GetMapping("/2fa/devices")
    public ApiResponse<List<TwoFactorService.TwoFactorDevice>> listTwoFactorDevices(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.credential.twoFactorDevicesListed"),
                twoFactorService.devices(jwt.getSubject()));
    }

    @DeleteMapping("/2fa/devices/{credentialId}")
    public ApiResponse<Void> removeTwoFactorDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String credentialId) {
        twoFactorService.removeDevice(jwt.getSubject(), credentialId);
        return ApiResponse.success(translator.translate("api.credential.twoFactorDeviceRemoved"), null);
    }
}
