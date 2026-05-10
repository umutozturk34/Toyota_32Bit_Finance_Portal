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

    private static final Map<String, Integer> RANGE_ORDER = Map.of(
            "1M", 0, "3M", 1, "6M", 2, "1Y", 3, "5Y", 4, "ALL", 5
    );

    private final UserChartPreferenceService preferenceService;
    private final UserChartDrawingService drawingService;
    private final ChartDefaultsProperties chartDefaults;

    @Transactional(readOnly = true)
    public UserChartBundleResponse getBundle(String userSub, TrackedAssetType type, String code, String range) {
        UserChartPreferenceResponse prefs = preferenceService.getOrDefault(userSub, type, code);
        UserChartDrawingResponse drawings = drawingService.getOrDefault(userSub, type, code);
        return new UserChartBundleResponse(prefs, filterByRange(drawings, range));
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

    private UserChartDrawingResponse filterByRange(UserChartDrawingResponse src, String viewRange) {
        if (src == null || src.drawings() == null || src.drawings().isEmpty()) return src;
        Integer viewOrder = viewRange == null ? null : RANGE_ORDER.get(viewRange);
        if (viewOrder == null) return src;
        List<Map<String, Object>> kept = src.drawings().stream()
                .filter(d -> {
                    Object r = d.get("range");
                    if (!(r instanceof String s)) return true;
                    Integer drawingOrder = RANGE_ORDER.get(s);
                    return drawingOrder == null || drawingOrder <= viewOrder;
                })
                .toList();
        return new UserChartDrawingResponse(kept, src.updatedAt());
    }
}
