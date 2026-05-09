package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.model.TrackedAssetType;
import com.finance.user.dto.UserChartPreferenceResponse;
import com.finance.user.dto.UserChartPreferenceUpdateRequest;
import com.finance.user.service.UserChartPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/chart-preferences")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserChartPreferenceController {

    private final UserChartPreferenceService service;

    @GetMapping("/{type}/{code}")
    public ApiResponse<UserChartPreferenceResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable TrackedAssetType type,
            @PathVariable String code) {
        return ApiResponse.success("Chart preferences retrieved",
                service.getOrDefault(jwt.getSubject(), type, code));
    }

    @PutMapping("/{type}/{code}")
    public ApiResponse<UserChartPreferenceResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @Valid @RequestBody UserChartPreferenceUpdateRequest request) {
        return ApiResponse.success("Chart preferences updated",
                service.upsert(jwt.getSubject(), type, code, request.config()));
    }
}
