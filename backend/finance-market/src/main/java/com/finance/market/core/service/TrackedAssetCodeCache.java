package com.finance.market.core.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class TrackedAssetCodeCache {

    private final TrackedAssetRepository repository;
    private final Cache<TrackedAssetType, List<String>> cache;

    public TrackedAssetCodeCache(TrackedAssetRepository repository, AppProperties appProperties) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(
                        appProperties.getTrackedAsset().getCodeCacheTtlSeconds()))
                .build();
    }

    public List<String> get(TrackedAssetType type) {
        return cache.get(type, this::loadFromRepository);
    }

    public void invalidate(TrackedAssetType type) {
        cache.invalidate(type);
    }

    private List<String> loadFromRepository(TrackedAssetType type) {
        return repository
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .map(TrackedAsset::getAssetCode)
                .toList();
    }
}
