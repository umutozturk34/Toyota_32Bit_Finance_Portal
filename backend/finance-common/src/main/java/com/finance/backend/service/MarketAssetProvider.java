package com.finance.backend.service;

import com.finance.backend.dto.response.GroupCount;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.MarketType;

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

    default List<MarketAssetResponse> getIndices() {
        return List.of();
    }

    record MarketAssetFilters(String segment, String subType) {
        public static MarketAssetFilters none() {
            return new MarketAssetFilters(null, null);
        }

        public static MarketAssetFilters ofSegment(String segment) {
            return new MarketAssetFilters(segment, null);
        }

        public boolean hasSegment() {
            return segment != null && !segment.isBlank();
        }

        public boolean hasSubType() {
            return subType != null && !subType.isBlank();
        }
    }
}
