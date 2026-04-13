package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

public final class MarketProviderHelper {

    private MarketProviderHelper() {}

    public static Sort buildSort(String sortBy, String direction, Map<String, String> fieldMapping) {
        String field = fieldMapping.getOrDefault(sortBy, fieldMapping.get("default"));
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }

    public static List<MarketAssetResponse> applyDisplayNames(
            List<MarketAssetResponse> responses, Map<String, String> displayNameMap) {
        return responses.stream()
                .map(r -> {
                    String displayName = displayNameMap.get(r.code());
                    if (displayName == null || displayName.isBlank()) return r;
                    return new MarketAssetResponse(r.code(), displayName, r.image(), r.type(),
                            r.price(), r.changeAmount(), r.changePercent(), r.lastUpdated(), r.metadata());
                })
                .toList();
    }
}
