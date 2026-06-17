package com.finance.user.service;

import tools.jackson.databind.JsonNode;
import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.user.config.ChartDefaultsProperties;
import com.finance.user.config.ChartDefaultsProperties.AssetTypeRules;
import com.finance.user.dto.UserChartBundleResponse;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.dto.UserChartPreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Coordinates reads and writes of a user's chart preferences and drawings, enforcing the configured
 * per-asset-type rules (allowed chart types, indicators, fib tools, drawing types, feature flags) and
 * count limits before delegating the actual upsert to the underlying services.
 */
@Service
@RequiredArgsConstructor
public class UserChartDataFacade {

    private final UserChartPreferenceService preferenceService;
    private final UserChartDrawingService drawingService;
    private final ChartDefaultsProperties chartDefaults;

    /** Bundles the user's preferences and drawings for an asset in one read, each falling back to its defaults when unsaved. */
    @Transactional(readOnly = true)
    public UserChartBundleResponse getBundle(String userSub, TrackedAssetType type, String code, String range) {
        UserChartPreferenceResponse prefs = preferenceService.getOrDefault(userSub, type, code);
        UserChartDrawingResponse drawings = drawingService.getOrDefault(userSub, type, code);
        return new UserChartBundleResponse(prefs, drawings);
    }

    /** Validates the config against asset-type rules and count limits, then upserts it. */
    public UserChartPreferenceResponse upsertPreferences(String userSub, TrackedAssetType type, String code,
                                                          JsonNode config) {
        validateAssetTypeRules(type, config);
        validateConfigLimits(config);
        return preferenceService.upsert(userSub, type, code, config);
    }

    /** Validates the drawings against asset-type rules and the per-asset count limit, then upserts them. */
    public UserChartDrawingResponse upsertDrawings(String userSub, TrackedAssetType type, String code,
                                                    JsonNode drawings) {
        validateDrawingsAssetType(type, drawings);
        validateDrawingLimit(drawings);
        return drawingService.upsert(userSub, type, code, drawings);
    }

    private void validateAssetTypeRules(TrackedAssetType type, JsonNode config) {
        AssetTypeRules rules = resolveRules(type);
        if (rules == null || config == null || !config.isObject()) return;

        JsonNode chartType = config.get("chartType");
        if (chartType != null && chartType.isString()
                && rules.allowedChartTypes() != null
                && !rules.allowedChartTypes().isEmpty()
                && !rules.allowedChartTypes().contains(chartType.asString())) {
            throw new BusinessException("error.chart.chartTypeNotAllowed", chartType.asString(), type);
        }

        rejectFlagIfDisallowed(config, "showVolume", rules.allowVolume(),
                "error.chart.volumeNotAllowed", type);
        rejectFlagIfDisallowed(config, "showInvestorCount", rules.allowInvestorCount(),
                "error.chart.investorCountNotAllowed", type);
        rejectFlagIfDisallowed(config, "showPortfolioSize", rules.allowPortfolioSize(),
                "error.chart.portfolioSizeNotAllowed", type);

        validateTypedArray(config, "indicators", rules.allowedIndicators(),
                "error.chart.indicatorTypeNotAllowed", type);
        validateTypedArray(config, "fibTools", rules.allowedFibTools(),
                "error.chart.fibToolTypeNotAllowed", type);
    }

    private void validateDrawingsAssetType(TrackedAssetType type, JsonNode drawings) {
        AssetTypeRules rules = resolveRules(type);
        if (rules == null || drawings == null || !drawings.isArray()) return;
        Set<String> allowed = rules.allowedDrawingTypes();
        if (allowed == null) return;
        for (JsonNode d : drawings) {
            String t = d.path("type").asString("");
            if (!t.isBlank() && !allowed.contains(t)) {
                throw new BusinessException("error.chart.drawingTypeNotAllowed", t, type);
            }
        }
    }

    private void rejectFlagIfDisallowed(JsonNode config, String field, boolean allowed,
                                         String messageKey, TrackedAssetType type) {
        if (allowed) return;
        JsonNode value = config.get(field);
        if (value != null && value.isBoolean() && value.asBoolean()) {
            throw new BusinessException(messageKey, type);
        }
    }

    private void validateTypedArray(JsonNode config, String field, Set<String> allowedTypes,
                                     String messageKey, TrackedAssetType type) {
        if (allowedTypes == null) return;
        JsonNode array = config.get(field);
        if (array == null || !array.isArray()) return;
        for (JsonNode item : array) {
            String t = item.path("type").asString("");
            if (!t.isBlank() && !allowedTypes.contains(t)) {
                throw new BusinessException(messageKey, t, type);
            }
        }
    }

    private AssetTypeRules resolveRules(TrackedAssetType type) {
        if (chartDefaults.rules() == null) return null;
        return chartDefaults.rules().get(type);
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
