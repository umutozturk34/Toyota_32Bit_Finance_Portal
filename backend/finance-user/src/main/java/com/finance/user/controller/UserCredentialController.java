package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.user.dto.EmailChangeInitiateRequest;
import com.finance.user.dto.PasswordChangeInitiateRequest;
import com.finance.user.service.UserCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/credentials")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserCredentialController {

    private final UserCredentialService service;

    @PostMapping("/password/initiate-change")
    public ApiResponse<Void> initiatePasswordChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeInitiateRequest request) {
        service.initiatePasswordChange(jwt.getSubject(), request.redirectUri());
        return ApiResponse.success("Password change link sent to email", null);
    }

    @PostMapping("/email/initiate-change")
    public ApiResponse<Void> initiateEmailChange(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmailChangeInitiateRequest request) {
        service.initiateEmailChange(jwt.getSubject(), request.newEmail(), request.redirectUri());
        return ApiResponse.success("Email verification code sent to new address", null);
    }
}
