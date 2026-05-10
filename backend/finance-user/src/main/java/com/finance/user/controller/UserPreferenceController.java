package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.user.dto.UserPreferenceResponse;
import com.finance.user.dto.UserPreferenceUpdateRequest;
import com.finance.user.service.UserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/preferences")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService service;
    private final Translator translator;

    @GetMapping
    public ApiResponse<UserPreferenceResponse> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(translator.translate("api.preferences.retrieved"), service.getOrDefault(jwt.getSubject()));
    }

    @PutMapping
    public ApiResponse<UserPreferenceResponse> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserPreferenceUpdateRequest request) {
        return ApiResponse.success(translator.translate("api.preferences.updated"), service.upsert(jwt.getSubject(), request));
    }
}
