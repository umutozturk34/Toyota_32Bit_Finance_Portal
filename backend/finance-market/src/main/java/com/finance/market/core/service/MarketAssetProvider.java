package com.finance.market.core.service;

import com.finance.shared.dto.response.GroupCount;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.common.model.MarketType;

import java.util.List;

public interface MarketAssetProvider {

    MarketType getType();

    MarketAssetResponse getByCode(String code);

    List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters, String sortBy, String direction, int page, int size);

    long count(MarketAssetFilters filters);

    long countBySearch(String searchTerm, MarketAssetFilters filters);

    List<MarketAssetResponse> getTopMovers(int limit, boolean gainers);

    default List<GroupCount> getGroupCounts() {
        return List.of();
    }

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
