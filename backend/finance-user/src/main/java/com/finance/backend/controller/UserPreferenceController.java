package com.finance.backend.controller;

import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.UserPreferenceResponse;
import com.finance.backend.dto.UserPreferenceUpdateRequest;
import com.finance.backend.service.UserPreferenceService;
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

    @GetMapping
    public ApiResponse<UserPreferenceResponse> getPreferences(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success("Preferences retrieved", service.getOrDefault(jwt.getSubject()));
    }

    @PutMapping
    public ApiResponse<UserPreferenceResponse> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserPreferenceUpdateRequest request) {
        return ApiResponse.success("Preferences updated", service.upsert(jwt.getSubject(), request));
    }
}
