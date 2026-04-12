package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.MarketType;

import java.util.List;

public interface MarketAssetProvider {

    MarketType getType();

    List<MarketAssetResponse> getAll();

    MarketAssetResponse getByCode(String code);

    List<MarketAssetResponse> search(String searchTerm, String sortBy, String direction, int page, int size);

    List<MarketAssetResponse> getTopMovers(int limit, boolean gainers);

    long count();

    long countBySearch(String searchTerm);

    default List<java.util.Map<String, Object>> getGroupCounts() {
        return List.of();
    }
}
