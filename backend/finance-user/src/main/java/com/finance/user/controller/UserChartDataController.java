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

/**
 * REST API for the current user's per-asset chart state: reads the combined config+drawings bundle
 * and upserts either side. All endpoints are authenticated and scoped to the JWT subject.
 */
@RestController
@RequestMapping("/api/v1/user/chart-data")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserChartDataController {

    private final UserChartDataFacade facade;
    private final Translator translator;

    /** Combined chart-config and drawings bundle for the {@code type}/{@code code} asset; {@code range} selects the saved timeframe variant. */
    @GetMapping("/{type}/{code}")
    public ApiResponse<UserChartBundleResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @RequestParam(required = false) String range) {
        return ApiResponse.success(translator.translate("api.chart.dataRetrieved"),
                facade.getBundle(jwt.getSubject(), type, code, range));
    }

    /** Upserts the chart preferences (indicators, styling) for the {@code type}/{@code code} asset. */
    @PutMapping("/{type}/{code}/preferences")
    public ApiResponse<UserChartPreferenceResponse> updatePreferences(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable TrackedAssetType type,
            @PathVariable String code,
            @Valid @RequestBody UserChartPreferenceUpdateRequest request) {
        return ApiResponse.success(translator.translate("api.chart.preferencesUpdated"),
                facade.upsertPreferences(jwt.getSubject(), type, code, request.config()));
    }

    /** Replaces the saved drawings (trendlines, annotations) for the {@code type}/{@code code} asset. */
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
