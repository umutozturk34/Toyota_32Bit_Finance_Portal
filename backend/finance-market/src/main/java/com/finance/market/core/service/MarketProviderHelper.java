package com.finance.market.core.service;

import com.finance.market.core.dto.response.MarketAssetResponse;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

/**
 * Shared helpers for market providers: translating API sort keys to entity fields and overlaying
 * curated display names onto responses.
 */
public final class MarketProviderHelper {

    private MarketProviderHelper() {}

    /**
     * Builds a DETERMINISTIC sort. A blank {@code sortBy} falls back to the mapping's {@code "default"} field
     * (or, absent that, the tiebreak field alone); an unmapped non-blank key passes through as a raw field.
     * {@code tiebreakField} is ALWAYS appended as an ascending secondary key, so equal primary values — or a
     * blank sort — never leave the row order to the database, which would otherwise return a different
     * arbitrary order on each reload (the "cards shuffle on F5" bug). Primary direction defaults to DESC; nulls
     * sort last.
     */
    public static Sort buildSort(String sortBy, String direction, Map<String, String> fieldMapping,
                                 String tiebreakField) {
        Sort.Order tiebreak = new Sort.Order(Sort.Direction.ASC, tiebreakField, Sort.NullHandling.NULLS_LAST);
        boolean blank = sortBy == null || sortBy.isBlank();
        String field = fieldMapping.get(blank ? "default" : sortBy);
        if (field == null) {
            if (blank) return Sort.by(tiebreak);
            field = sortBy;
        }
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort.Order primary = new Sort.Order(dir, field, Sort.NullHandling.NULLS_LAST);
        return field.equals(tiebreakField) ? Sort.by(primary) : Sort.by(primary, tiebreak);
    }

    /** Replaces each response's name with its curated display name when one is mapped for the code. */
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
