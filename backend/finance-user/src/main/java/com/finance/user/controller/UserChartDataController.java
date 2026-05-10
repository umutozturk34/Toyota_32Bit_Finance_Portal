package com.finance.user.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.common.model.TrackedAssetType;
import com.finance.user.dto.UserChartBundleResponse;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.dto.UserChartDrawingUpdateRequest;
import com.finance.user.dto.UserChartPreferenceResponse;
import com.finance.user.dto.UserChartPreferenceUpdateRequest;
import com.finance.user.service.UserChartDataFacade;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/chart-data")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserChartDataController {

    private final UserChartDataFacade facade;
    private final Translator translator;

    @GetMapping("/{type}/{code}")
    public ApiResponse<UserChartBundleResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @RequestParam(required = false) String range) {
        return ApiResponse.success(translator.translate("api.chart.dataRetrieved"),
                facade.getBundle(jwt.getSubject(), type, code, range));
    }

    @PutMapping("/{type}/{code}/preferences")
    public ApiResponse<UserChartPreferenceResponse> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @Valid @RequestBody UserChartPreferenceUpdateRequest request) {
        return ApiResponse.success(translator.translate("api.chart.preferencesUpdated"),
                facade.upsertPreferences(jwt.getSubject(), type, code, request.config()));
    }

    @PutMapping("/{type}/{code}/drawings")
    public ApiResponse<UserChartDrawingResponse> updateDrawings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @Valid @RequestBody UserChartDrawingUpdateRequest request) {
        return ApiResponse.success(translator.translate("api.chart.drawingsUpdated"),
                facade.upsertDrawings(jwt.getSubject(), type, code, request.drawings()));
    }
}
