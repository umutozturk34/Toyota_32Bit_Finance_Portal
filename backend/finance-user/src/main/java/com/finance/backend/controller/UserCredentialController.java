package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.PasswordChangeInitiateRequest;
import com.finance.backend.service.UserCredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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
}
