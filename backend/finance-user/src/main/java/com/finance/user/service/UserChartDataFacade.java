package com.finance.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.user.config.ChartDefaultsProperties;
import com.finance.user.dto.UserChartBundleResponse;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.dto.UserChartPreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserChartDataFacade {

    private final UserChartPreferenceService preferenceService;
    private final UserChartDrawingService drawingService;
    private final ChartDefaultsProperties chartDefaults;

    @Transactional(readOnly = true)
    public UserChartBundleResponse getBundle(String userSub, TrackedAssetType type, String code, String range) {
        UserChartPreferenceResponse prefs = preferenceService.getOrDefault(userSub, type, code);
        UserChartDrawingResponse drawings = drawingService.getOrDefault(userSub, type, code);
        return new UserChartBundleResponse(prefs, drawings);
    }

    public UserChartPreferenceResponse upsertPreferences(String userSub, TrackedAssetType type, String code,
                                                          JsonNode config) {
        validateConfigLimits(config);
        return preferenceService.upsert(userSub, type, code, config);
    }

    public UserChartDrawingResponse upsertDrawings(String userSub, TrackedAssetType type, String code,
                                                    JsonNode drawings) {
        validateDrawingLimit(drawings);
        return drawingService.upsert(userSub, type, code, drawings);
    }

    private void validateConfigLimits(JsonNode config) {
        if (config == null || config.isNull() || chartDefaults.limits() == null) return;
        int maxIndicators = chartDefaults.limits().maxIndicatorsPerAsset();
        int maxFibTools = chartDefaults.limits().maxFibToolsPerAsset();
        JsonNode indicators = config.get("indicators");
        if (indicators != null && indicators.isArray() && maxIndicators > 0 && indicators.size() > maxIndicators) {
            throw new BusinessException("error.chart.indicatorLimit", maxIndicators);
        }
        JsonNode fibTools = config.get("fibTools");
        if (fibTools != null && fibTools.isArray() && maxFibTools > 0 && fibTools.size() > maxFibTools) {
            throw new BusinessException("error.chart.fibToolLimit", maxFibTools);
        }
    }

    private void validateDrawingLimit(JsonNode drawings) {
        if (drawings == null || drawings.isNull() || chartDefaults.limits() == null) return;
        int max = chartDefaults.limits().maxDrawingsPerAsset();
        if (max > 0 && drawings.isArray() && drawings.size() > max) {
            throw new BusinessException("error.chart.drawingLimit", max);
        }
    }
}
