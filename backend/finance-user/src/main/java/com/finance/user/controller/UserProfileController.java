package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.ProfileResponse;
import com.finance.user.dto.ProfileUpdateRequest;
import com.finance.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/profile")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService service;
    private final Translator translator;

    @GetMapping
    public ApiResponse<ProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.profile.retrieved"), service.get(jwt.getSubject()));
    }

    @PutMapping
    public ApiResponse<ProfileResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(translator.translate("api.profile.updated"), service.update(jwt.getSubject(), request));
    }
}
