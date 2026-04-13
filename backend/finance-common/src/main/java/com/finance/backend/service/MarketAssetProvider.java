package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.MarketType;

import java.util.List;
import java.util.Map;

public interface MarketAssetProvider {

    MarketType getType();

    MarketAssetResponse getByCode(String code);

    List<MarketAssetResponse> search(String searchTerm, Map<String, String> filters, String sortBy, String direction, int page, int size);

    long count(Map<String, String> filters);

    long countBySearch(String searchTerm, Map<String, String> filters);

    List<MarketAssetResponse> getTopMovers(int limit, boolean gainers);

    default List<Map<String, Object>> getGroupCounts() {
        return List.of();
    }
}
