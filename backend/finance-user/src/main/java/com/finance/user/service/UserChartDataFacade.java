package com.finance.user.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.user.config.ChartDefaultsProperties;
import com.finance.user.dto.UserChartBundleResponse;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.dto.UserChartPreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
                                                          Map<String, Object> config) {
        validateConfigLimits(config);
        return preferenceService.upsert(userSub, type, code, config);
    }

    public UserChartDrawingResponse upsertDrawings(String userSub, TrackedAssetType type, String code,
                                                    List<Map<String, Object>> drawings) {
        validateDrawingLimit(drawings);
        return drawingService.upsert(userSub, type, code, drawings);
    }

    private void validateConfigLimits(Map<String, Object> config) {
        if (config == null || chartDefaults.limits() == null) return;
        int maxIndicators = chartDefaults.limits().maxIndicatorsPerAsset();
        int maxFibTools = chartDefaults.limits().maxFibToolsPerAsset();
        Object indicators = config.get("indicators");
        if (indicators instanceof List<?> list && maxIndicators > 0 && list.size() > maxIndicators) {
            throw new BusinessException("error.chart.indicatorLimit", maxIndicators);
        }
        Object fibTools = config.get("fibTools");
        if (fibTools instanceof List<?> list && maxFibTools > 0 && list.size() > maxFibTools) {
            throw new BusinessException("error.chart.fibToolLimit", maxFibTools);
        }
    }

    private void validateDrawingLimit(List<Map<String, Object>> drawings) {
        if (drawings == null || chartDefaults.limits() == null) return;
        int max = chartDefaults.limits().maxDrawingsPerAsset();
        if (max > 0 && drawings.size() > max) {
            throw new BusinessException("error.chart.drawingLimit", max);
        }
    }
}
