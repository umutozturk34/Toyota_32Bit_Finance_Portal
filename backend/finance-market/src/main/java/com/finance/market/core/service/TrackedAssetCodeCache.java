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
    private final Cache<TrackedAssetType, List<String>> allCache;
    private final Cache<TrackedAssetType, List<String>> enabledCache;

    public TrackedAssetCodeCache(TrackedAssetRepository repository, AppProperties appProperties) {
        this.repository = repository;
        Duration ttl = Duration.ofSeconds(
                appProperties.getTrackedAsset().getCodeCacheTtlSeconds());
        this.allCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
        this.enabledCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
    }

    public List<String> get(TrackedAssetType type) {
        return allCache.get(type, this::loadAllFromRepository);
    }

    public List<String> getEnabled(TrackedAssetType type) {
        return enabledCache.get(type, this::loadEnabledFromRepository);
    }

    public void invalidate(TrackedAssetType type) {
        allCache.invalidate(type);
        enabledCache.invalidate(type);
    }

    private List<String> loadAllFromRepository(TrackedAssetType type) {
        return repository
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .map(TrackedAsset::getAssetCode)
                .toList();
    }

    private List<String> loadEnabledFromRepository(TrackedAssetType type) {
        return repository
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .map(TrackedAsset::getAssetCode)
                .toList();
    }
}
