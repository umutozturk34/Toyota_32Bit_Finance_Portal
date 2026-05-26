package com.finance.market.core.service;

import com.finance.market.core.dto.response.MarketAssetResponse;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

public final class MarketProviderHelper {

    private MarketProviderHelper() {}

    public static Sort buildSort(String sortBy, String direction, Map<String, String> fieldMapping) {
        if (sortBy == null || sortBy.isBlank()) return Sort.unsorted();
        String field = fieldMapping.getOrDefault(sortBy, sortBy);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order order = new Sort.Order(dir, field, Sort.NullHandling.NULLS_LAST);
        return Sort.by(order);
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
