package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import com.finance.backend.dto.internal.TrackedAssetUpsertCommand;
import com.finance.backend.dto.request.TrackedAssetOrderItemRequest;
import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.TrackedAssetMapper;
import com.finance.backend.model.StockSegment;
import com.finance.backend.model.TrackedAsset;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.TrackedAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class TrackedAssetService {
    private final AppProperties appProperties;
    private final TrackedAssetRepository trackedAssetRepository;
    private final TrackedAssetMapper trackedAssetMapper;
    private final Map<TrackedAssetType, CachedCodeList> activeCodeCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<TrackedAssetResponse> getTrackedAssets(TrackedAssetType type, boolean includeDisabled) {
        List<TrackedAsset> assets = includeDisabled
                ? trackedAssetRepository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type)
                : trackedAssetRepository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type);
        return assets.stream()
            .map(trackedAssetMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrackedAssetResponse> searchTrackedAssets(List<TrackedAssetType> types,
                                                           boolean includeDisabled,
                                                           String search,
                                                           String sortBy,
                                                           String direction) {
        List<TrackedAsset> all = new ArrayList<>();
        for (TrackedAssetType type : types) {
            List<TrackedAsset> assets = includeDisabled
                    ? trackedAssetRepository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type)
                    : trackedAssetRepository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type);
            all.addAll(assets);
        }

        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            all = all.stream()
                    .filter(a -> a.getAssetCode().toLowerCase().contains(lower)
                            || (a.getDisplayName() != null && a.getDisplayName().toLowerCase().contains(lower)))
                    .toList();
        }

        Comparator<TrackedAsset> comparator = buildTrackedAssetComparator(sortBy);
        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        all = all.stream().sorted(comparator).toList();

        return all.stream().map(trackedAssetMapper::toResponse).toList();
    }

    private Comparator<TrackedAsset> buildTrackedAssetComparator(String sortBy) {
        return switch (sortBy != null ? sortBy : "sortOrder") {
            case "assetCode" -> Comparator.comparing(TrackedAsset::getAssetCode);
            case "displayName" -> Comparator.comparing(
                    TrackedAsset::getDisplayName, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(TrackedAsset::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TrackedAsset::getAssetCode);
        };
    }

    @Transactional(readOnly = true)
    public Optional<TrackedAssetResponse> getTrackedAsset(TrackedAssetType type, String assetCode) {
        String normalizedCode = normalizeCode(type, assetCode);
        return trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
            .map(trackedAssetMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public void ensureEnabledOrThrow(TrackedAssetType type, String assetCode) {
        resolveEnabledCodeOrThrow(type, assetCode);
    }

    @Transactional(readOnly = true)
    public String resolveEnabledCodeOrThrow(TrackedAssetType type, String assetCode) {
        String normalizedCode = normalizeCode(type, assetCode);
        boolean enabled = trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .map(TrackedAsset::isEnabled)
                .orElse(false);
        if (!enabled) {
            throw new ResourceNotFoundException("Tracked asset not found or disabled: " + type + " / " + normalizedCode);
        }
        return normalizedCode;
    }

    @Transactional(readOnly = true)
    public List<String> getEnabledCodes(TrackedAssetType type) {
        CachedCodeList cached = activeCodeCache.get(type);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.codes();
        }

        List<String> codes = trackedAssetRepository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .map(TrackedAsset::getAssetCode)
                .toList();
        activeCodeCache.put(type, new CachedCodeList(
                codes,
                Instant.now().plusSeconds(appProperties.getTrackedAsset().getCodeCacheTtlSeconds())));
        return codes;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getEnabledDisplayNameMap(TrackedAssetType type) {
        return trackedAssetRepository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .filter(asset -> asset.getDisplayName() != null && !asset.getDisplayName().isBlank())
                .collect(Collectors.toMap(
                        TrackedAsset::getAssetCode,
                        TrackedAsset::getDisplayName,
                        (first, second) -> second,
                        LinkedHashMap::new
                ));
    }



    @Transactional
    public TrackedAssetResponse upsert(TrackedAssetUpsertCommand command) {
        TrackedAssetType type = command.getAssetType();
        String normalizedCode = normalizeCode(type, command.getAssetCode());

        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseGet(() -> TrackedAsset.builder()
                        .assetType(type)
                        .assetCode(normalizedCode)
                        .build());

        entity.setDisplayName(resolveDisplayName(normalizedCode, command.getDisplayName(), entity.getDisplayName()));
        entity.setBinanceSymbol(resolveBinanceSymbol(type, command.getBinanceSymbol()));
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
        entity.setSortOrder(command.getSortOrder() == null ? 0 : command.getSortOrder());
        StockSegment resolvedSegment = resolveStockSegment(type, command.getStockSegment(), entity.getStockSegment());
        entity.setStockSegment(resolvedSegment);
        entity.setIndexAsset(resolveIndexAsset(type, resolvedSegment, command.getIndexAsset(), entity.isIndexAsset()));
        entity.setCompareOnly(resolveCompareOnly(type, resolvedSegment, command.getCompareOnly(), entity.isCompareOnly()));

        TrackedAsset saved = trackedAssetRepository.save(entity);
        invalidate(type);
        return trackedAssetMapper.toResponse(saved);
    }

    @Transactional
    public void setEnabled(TrackedAssetType type, String assetCode, boolean enabled) {
        String normalizedCode = normalizeCode(type, assetCode);
        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
        entity.setEnabled(enabled);
        trackedAssetRepository.save(entity);
        invalidate(type);
    }

    @Transactional
    public void delete(TrackedAssetType type, String assetCode) {
        String normalizedCode = normalizeCode(type, assetCode);
        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
        trackedAssetRepository.delete(entity);
        invalidate(type);
    }

    @Transactional
    public void updateSortOrders(TrackedAssetType type, List<TrackedAssetOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<TrackedAsset> entitiesToUpdate = new ArrayList<>();
        for (TrackedAssetOrderItemRequest item : items) {
            String normalizedCode = normalizeCode(type, item.getAssetCode());
            TrackedAsset entity = trackedAssetRepository
                    .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
            entity.setSortOrder(item.getSortOrder());
            entitiesToUpdate.add(entity);
        }

        trackedAssetRepository.saveAll(entitiesToUpdate);
        invalidate(type);
    }

    @Transactional
    public void bootstrapIfEmpty(TrackedAssetType type, List<String> fallbackCodes) {
        if (fallbackCodes == null || fallbackCodes.isEmpty()) {
            return;
        }
        if (trackedAssetRepository.existsByAssetType(type)) {
            return;
        }

        List<TrackedAsset> seedData = new ArrayList<>();
        for (int i = 0; i < fallbackCodes.size(); i++) {
            String normalizedCode = normalizeCode(type, fallbackCodes.get(i));
            TrackedAsset asset = TrackedAsset.builder()
                .assetType(type)
                .assetCode(normalizedCode)
                .enabled(true)
                .sortOrder(orderForSeed(i))
                .build();
            StockSegment resolvedSegment = resolveStockSegment(type, null, null);
            asset.setStockSegment(resolvedSegment);
            asset.setIndexAsset(resolveIndexAsset(type, resolvedSegment, null, false));
            asset.setCompareOnly(resolveCompareOnly(type, resolvedSegment, null, false));
            asset.setDisplayName(resolveDisplayName(normalizedCode, null, null));
            seedData.add(asset);
        }

        trackedAssetRepository.saveAll(seedData);
        invalidate(type);
        log.info("Bootstrapped {} tracked assets for {}", seedData.size(), type);
    }

    private int orderForSeed(int order) {
        return order;
    }

    @Transactional(readOnly = true)
    public Optional<String> getCryptoBinanceSymbol(String coinGeckoId) {
        String normalizedCode = normalizeCode(TrackedAssetType.CRYPTO, coinGeckoId);
        return trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, normalizedCode)
                .map(TrackedAsset::getBinanceSymbol)
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty());
    }

    private StockSegment resolveStockSegment(TrackedAssetType type, StockSegment requestedSegment, StockSegment existingSegment) {
        if (type != TrackedAssetType.STOCK) {
            return null;
        }
        if (requestedSegment != null) {
            return requestedSegment;
        }
        if (existingSegment != null) {
            return existingSegment;
        }
        return StockSegment.EQUITY;
    }

    private String resolveDisplayName(String normalizedCode, String requestedDisplayName, String existingDisplayName) {
        if (requestedDisplayName == null) {
            return existingDisplayName;
        }
        if (requestedDisplayName.isBlank()) {
            return null;
        }
        return requestedDisplayName.trim();
    }

    private boolean resolveIndexAsset(TrackedAssetType type,
                                      StockSegment resolvedSegment,
                                      Boolean requested,
                                      boolean existing) {
        if (type != TrackedAssetType.STOCK) {
            return false;
        }
        if (requested != null) {
            return requested;
        }
        if (resolvedSegment != null && resolvedSegment != StockSegment.EQUITY) {
            return true;
        }
        return existing;
    }

    private boolean resolveCompareOnly(TrackedAssetType type,
                                       StockSegment resolvedSegment,
                                       Boolean requested,
                                       boolean existing) {
        if (type != TrackedAssetType.STOCK) {
            return false;
        }
        if (requested != null) {
            return requested;
        }
        if (resolvedSegment == StockSegment.SECONDARY_INDEX) {
            return true;
        }
        return existing;
    }

    private String resolveBinanceSymbol(TrackedAssetType type, String requestedBinanceSymbol) {
        if (type != TrackedAssetType.CRYPTO) {
            return null;
        }
        if (requestedBinanceSymbol == null || requestedBinanceSymbol.isBlank()) {
            return null;
        }
        return requestedBinanceSymbol.trim().toUpperCase();
    }

    private String normalizeCode(TrackedAssetType type, String assetCode) {
        String trimmed = assetCode == null ? "" : assetCode.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Asset code cannot be blank");
        }
        return switch (type) {
            case CRYPTO -> trimmed.toLowerCase();
            case STOCK, FUND -> trimmed.toUpperCase();
        };
    }

    private void invalidate(TrackedAssetType type) {
        activeCodeCache.remove(type);
    }

    private record CachedCodeList(List<String> codes, Instant expiresAt) {
    }
}
