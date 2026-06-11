package com.finance.market.core.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Caffeine cache of tracked asset codes per {@link TrackedAssetType} (all vs. enabled-only) plus the
 * curated code-to-display-name map, with a configurable TTL, sparing hot query paths repeated repository
 * hits. Invalidated on tracked-asset mutations.
 */
@Component
public class TrackedAssetCodeCache {

    private final TrackedAssetRepository repository;
    private final Cache<TrackedAssetType, List<String>> allCache;
    private final Cache<TrackedAssetType, List<String>> enabledCache;
    private final Cache<TrackedAssetType, Map<String, String>> displayNameCache;

    public TrackedAssetCodeCache(TrackedAssetRepository repository, AppProperties appProperties) {
        this.repository = repository;
        Duration ttl = Duration.ofSeconds(
                appProperties.getTrackedAsset().getCodeCacheTtlSeconds());
        this.allCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
        this.enabledCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
        this.displayNameCache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
    }

    public List<String> get(TrackedAssetType type) {
        return allCache.get(type, this::loadAllFromRepository);
    }

    public List<String> getEnabled(TrackedAssetType type) {
        return enabledCache.get(type, this::loadEnabledFromRepository);
    }

    /** Code-to-curated-display-name map (insertion-ordered) for assets that have a non-blank name. */
    public Map<String, String> getDisplayNames(TrackedAssetType type) {
        return displayNameCache.get(type, this::loadDisplayNamesFromRepository);
    }

    public void invalidate(TrackedAssetType type) {
        allCache.invalidate(type);
        enabledCache.invalidate(type);
        displayNameCache.invalidate(type);
    }

    private List<String> loadAllFromRepository(TrackedAssetType type) {
        return repository
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .map(TrackedAsset::getAssetCode)
                .toList();
    }

    private Map<String, String> loadDisplayNamesFromRepository(TrackedAssetType type) {
        return repository
                .findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .filter(asset -> asset.getDisplayName() != null && !asset.getDisplayName().isBlank())
                .collect(Collectors.toMap(
                        TrackedAsset::getAssetCode,
                        TrackedAsset::getDisplayName,
                        (first, second) -> second,
                        LinkedHashMap::new));
    }

    private List<String> loadEnabledFromRepository(TrackedAssetType type) {
        return repository
                .findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .map(TrackedAsset::getAssetCode)
                .toList();
    }
}
