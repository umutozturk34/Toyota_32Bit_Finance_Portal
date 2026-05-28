package com.finance.market.core.service;

import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.dto.request.TrackedAssetOrderItemRequest;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.core.mapper.TrackedAssetMapper;
import com.finance.common.model.Instrument;
import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Write side for the tracked-asset catalogue (which instruments the platform actively follows).
 * Every mutation normalizes the code, links/creates the shared {@link Instrument}, applies
 * per-type resolution rules, persists, and invalidates the code cache.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@Transactional
public class TrackedAssetCommandService {
    private final TrackedAssetRepository trackedAssetRepository;
    private final TrackedAssetMapper trackedAssetMapper;
    private final TrackedAssetCodeCache codeCache;
    private final AssetRegistryService assetRegistry;

    /** Creates or updates a tracked asset, delegating field defaults to the type's resolve rules. */
    public TrackedAssetResponse upsert(TrackedAssetUpsertCommand command) {
        TrackedAssetType type = command.getAssetType();
        String normalizedCode = type.normalizeCode(command.getAssetCode());

        Instrument asset = assetRegistry.upsert(type.marketType(), normalizedCode);

        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseGet(() -> TrackedAsset.builder()
                        .assetType(type)
                        .assetCode(normalizedCode)
                        .build());

        entity.setAsset(asset);
        entity.setDisplayName(resolveDisplayName(command.getDisplayName(), entity.getDisplayName()));
        entity.setBinanceSymbol(type.resolveBinanceSymbol(command.getBinanceSymbol()));
        entity.setSortOrder(command.getSortOrder() == null ? 0 : command.getSortOrder());
        StockSegment resolvedSegment = type.resolveSegment(command.getStockSegment(), entity.getStockSegment());
        entity.setStockSegment(resolvedSegment);
        entity.setIndexAsset(type.resolveIndexAsset(resolvedSegment, command.getIndexAsset(), entity.isIndexAsset()));
        entity.setCompareOnly(type.resolveCompareOnly(resolvedSegment, command.getCompareOnly(), entity.isCompareOnly()));

        TrackedAsset saved = trackedAssetRepository.save(entity);
        codeCache.invalidate(type);
        return trackedAssetMapper.toResponse(saved);
    }

    /** Auto-registers a newly discovered asset as enabled; no-op if it is already tracked. */
    public void autoTrack(TrackedAssetType type, String assetCode, String defaultDisplayName, int sortOrder) {
        String normalizedCode = type.normalizeCode(assetCode);
        if (trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode).isPresent()) {
            return;
        }
        Instrument asset = assetRegistry.upsert(type.marketType(), normalizedCode);
        StockSegment segment = type.resolveSegment(null, null);
        TrackedAsset entity = TrackedAsset.builder()
                .assetType(type)
                .assetCode(normalizedCode)
                .asset(asset)
                .displayName(defaultDisplayName)
                .sortOrder(sortOrder)
                .stockSegment(segment)
                .indexAsset(type.resolveIndexAsset(segment, null, false))
                .compareOnly(type.resolveCompareOnly(segment, null, false))
                .enabled(true)
                .build();
        trackedAssetRepository.save(entity);
        codeCache.invalidate(type);
    }

    public void delete(TrackedAssetType type, String assetCode) {
        String normalizedCode = type.normalizeCode(assetCode);
        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("error.trackedAsset.notFound", type, normalizedCode));
        trackedAssetRepository.delete(entity);
        codeCache.invalidate(type);
    }

    public void updateSortOrders(TrackedAssetType type, List<TrackedAssetOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<TrackedAsset> entitiesToUpdate = new ArrayList<>();
        for (TrackedAssetOrderItemRequest item : items) {
            String normalizedCode = type.normalizeCode(item.getAssetCode());
            TrackedAsset entity = trackedAssetRepository
                    .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                    .orElseThrow(() -> new ResourceNotFoundException("error.trackedAsset.notFound", type, normalizedCode));
            entity.setSortOrder(item.getSortOrder());
            entitiesToUpdate.add(entity);
        }

        trackedAssetRepository.saveAll(entitiesToUpdate);
        codeCache.invalidate(type);
    }

    /** Keeps the existing name when none is requested, clears it on blank, otherwise trims the request. */
    private String resolveDisplayName(String requestedDisplayName, String existingDisplayName) {
        if (requestedDisplayName == null) {
            return existingDisplayName;
        }
        if (requestedDisplayName.isBlank()) {
            return null;
        }
        return requestedDisplayName.trim();
    }
}
