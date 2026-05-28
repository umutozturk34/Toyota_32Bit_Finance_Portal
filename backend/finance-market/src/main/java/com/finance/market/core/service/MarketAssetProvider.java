package com.finance.market.core.service;

import com.finance.shared.dto.response.GroupCount;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.common.model.MarketType;

import java.util.List;

/**
 * Read-side facade for one market: code lookup, paged search/count, and top movers. Implementations
 * are dispatched by {@link #getType()} so callers stay market-agnostic.
 */
public interface MarketAssetProvider {

    /** Market this provider serves. */
    MarketType getType();

    MarketAssetResponse getByCode(String code);

    List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters, String sortBy, String direction, int page, int size);

    long count(MarketAssetFilters filters);

    long countBySearch(String searchTerm, MarketAssetFilters filters);

    /** Largest movers; {@code gainers=false} returns losers. */
    List<MarketAssetResponse> getTopMovers(int limit, boolean gainers);

    /** Per-grouping asset counts (e.g. by segment); empty unless the market overrides. */
    default List<GroupCount> getGroupCounts() {
        return List.of();
    }

    /** Optional search facets; null/blank/empty fields mean "no constraint" per the {@code has*} checks. */
    record MarketAssetFilters(String segment, String subType,
                              java.util.List<String> subCategories,
                              java.util.List<Integer> riskValues) {
        public MarketAssetFilters(String segment, String subType) {
            this(segment, subType, null, null);
        }

        public static MarketAssetFilters none() {
            return new MarketAssetFilters(null, null, null, null);
        }

        public static MarketAssetFilters ofSegment(String segment) {
            return new MarketAssetFilters(segment, null, null, null);
        }

        public boolean hasSegment() {
            return segment != null && !segment.isBlank();
        }

        public boolean hasSubType() {
            return subType != null && !subType.isBlank();
        }

        public boolean hasSubCategories() {
            return subCategories != null && !subCategories.isEmpty();
        }

        public boolean hasRiskValues() {
            return riskValues != null && !riskValues.isEmpty();
        }
    }
}
