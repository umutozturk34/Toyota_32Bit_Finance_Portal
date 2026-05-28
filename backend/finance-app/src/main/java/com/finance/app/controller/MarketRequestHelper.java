package com.finance.app.controller;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.shared.util.EnumParser;

import java.util.Arrays;
import java.util.List;

/**
 * Static helpers for market controller request parsing: turns a comma-separated type string into the
 * matching enum list (all values when blank, bad-request on an invalid token) and clamps page sizes.
 */
public final class MarketRequestHelper {

    private MarketRequestHelper() {}

    public static List<MarketType> parseMarketTypes(String type) {
        if (type == null || type.isBlank()) return List.of(MarketType.values());
        return Arrays.stream(type.split(","))
                .map(raw -> EnumParser.parseOrBadRequest(MarketType.class, raw.trim().toUpperCase(), "enum.field.marketType"))
                .toList();
    }

    public static List<TrackedAssetType> parseTrackedTypes(String type) {
        if (type == null || type.isBlank()) return List.of(TrackedAssetType.values());
        return Arrays.stream(type.split(","))
                .map(raw -> EnumParser.parseOrBadRequest(TrackedAssetType.class, raw.trim().toUpperCase(), "enum.field.trackedAssetType"))
                .toList();
    }

    public static int clamp(Integer value, int defaultVal, int max) {
        int resolved = value == null ? defaultVal : value;
        return Math.max(1, Math.min(resolved, max));
    }
}
