package com.finance.backend.controller;

import com.finance.backend.exception.BadRequestException;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;

import java.util.Arrays;
import java.util.List;

final class MarketRequestHelper {

    private MarketRequestHelper() {}

    static List<MarketType> parseMarketTypes(String type) {
        if (type == null || type.isBlank()) return List.of(MarketType.values());
        try {
            return Arrays.stream(type.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .map(MarketType::valueOf)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid market type: " + type);
        }
    }

    static List<TrackedAssetType> parseTrackedTypes(String type) {
        if (type == null || type.isBlank()) return List.of(TrackedAssetType.values());
        return Arrays.stream(type.split(","))
                .map(String::trim)
                .map(TrackedAssetType::valueOf)
                .toList();
    }

    static int clamp(Integer value, int defaultVal, int max) {
        int resolved = value == null ? defaultVal : value;
        return Math.max(1, Math.min(resolved, max));
    }
}
