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
        String normalizedCode = type.normalizeCode(assetCode);
        return trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
            .map(trackedAssetMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public void ensureEnabledOrThrow(TrackedAssetType type, String assetCode) {
        resolveEnabledCodeOrThrow(type, assetCode);
    }

    @Transactional(readOnly = true)
    public String resolveEnabledCodeOrThrow(TrackedAssetType type, String assetCode) {
        String normalizedCode = type.normalizeCode(assetCode);
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
        String normalizedCode = type.normalizeCode(command.getAssetCode());

        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseGet(() -> TrackedAsset.builder()
                        .assetType(type)
                        .assetCode(normalizedCode)
                        .build());

        entity.setDisplayName(resolveDisplayName(command.getDisplayName(), entity.getDisplayName()));
        entity.setBinanceSymbol(type.resolveBinanceSymbol(command.getBinanceSymbol()));
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
        entity.setSortOrder(command.getSortOrder() == null ? 0 : command.getSortOrder());
        StockSegment resolvedSegment = type.resolveSegment(command.getStockSegment(), entity.getStockSegment());
        entity.setStockSegment(resolvedSegment);
        entity.setIndexAsset(type.resolveIndexAsset(resolvedSegment, command.getIndexAsset(), entity.isIndexAsset()));
        entity.setCompareOnly(type.resolveCompareOnly(resolvedSegment, command.getCompareOnly(), entity.isCompareOnly()));

        TrackedAsset saved = trackedAssetRepository.save(entity);
        invalidate(type);
        return trackedAssetMapper.toResponse(saved);
    }

    @Transactional
    public void setEnabled(TrackedAssetType type, String assetCode, boolean enabled) {
        String normalizedCode = type.normalizeCode(assetCode);
        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
        entity.setEnabled(enabled);
        trackedAssetRepository.save(entity);
        invalidate(type);
    }

    @Transactional
    public void delete(TrackedAssetType type, String assetCode) {
        String normalizedCode = type.normalizeCode(assetCode);
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
            String normalizedCode = type.normalizeCode(item.getAssetCode());
            TrackedAsset entity = trackedAssetRepository
                    .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
            entity.setSortOrder(item.getSortOrder());
            entitiesToUpdate.add(entity);
        }

        trackedAssetRepository.saveAll(entitiesToUpdate);
        invalidate(type);
    }

    @Transactional(readOnly = true)
    public Optional<String> getCryptoBinanceSymbol(String coinGeckoId) {
        String normalizedCode = TrackedAssetType.CRYPTO.normalizeCode(coinGeckoId);
        return trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, normalizedCode)
                .map(TrackedAsset::getBinanceSymbol)
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty());
    }

    private String resolveDisplayName(String requestedDisplayName, String existingDisplayName) {
        if (requestedDisplayName == null) {
            return existingDisplayName;
        }
        if (requestedDisplayName.isBlank()) {
            return null;
        }
        return requestedDisplayName.trim();
    }

    private void invalidate(TrackedAssetType type) {
        activeCodeCache.remove(type);
    }

    private record CachedCodeList(List<String> codes, Instant expiresAt) {
    }
}
