package com.finance.backend.controller;

import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.util.EnumParser;

import java.util.Arrays;
import java.util.List;

final class MarketRequestHelper {

    private MarketRequestHelper() {}

    static List<MarketType> parseMarketTypes(String type) {
        if (type == null || type.isBlank()) return List.of(MarketType.values());
        return Arrays.stream(type.split(","))
                .map(raw -> EnumParser.parseOrBadRequest(MarketType.class, raw.trim().toUpperCase(), "market type"))
                .toList();
    }

    static List<TrackedAssetType> parseTrackedTypes(String type) {
        if (type == null || type.isBlank()) return List.of(TrackedAssetType.values());
        return Arrays.stream(type.split(","))
                .map(raw -> EnumParser.parseOrBadRequest(TrackedAssetType.class, raw.trim().toUpperCase(), "tracked asset type"))
                .toList();
    }

    static int clamp(Integer value, int defaultVal, int max) {
        int resolved = value == null ? defaultVal : value;
        return Math.max(1, Math.min(resolved, max));
    }
}
