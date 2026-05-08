package com.finance.market.core.service;

import com.finance.market.core.dto.internal.TrackedAssetUpsertCommand;
import com.finance.market.core.dto.request.TrackedAssetOrderItemRequest;
import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.core.mapper.TrackedAssetMapper;
import com.finance.common.model.Asset;
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

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional
public class TrackedAssetCommandService {
    private final TrackedAssetRepository trackedAssetRepository;
    private final TrackedAssetMapper trackedAssetMapper;
    private final TrackedAssetCodeCache codeCache;
    private final AssetRegistryService assetRegistry;

    public TrackedAssetResponse upsert(TrackedAssetUpsertCommand command) {
        TrackedAssetType type = command.getAssetType();
        String normalizedCode = type.normalizeCode(command.getAssetCode());

        Asset asset = assetRegistry.upsert(type.marketType(), normalizedCode, command.getDisplayName());

        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseGet(() -> TrackedAsset.builder()
                        .assetType(type)
                        .assetCode(normalizedCode)
                        .build());

        entity.setAsset(asset);
        entity.setDisplayName(resolveDisplayName(command.getDisplayName(), entity.getDisplayName()));
        entity.setBinanceSymbol(type.resolveBinanceSymbol(command.getBinanceSymbol()));
        entity.setEnabled(command.getEnabled() == null || command.getEnabled());
        entity.setSortOrder(command.getSortOrder() == null ? 0 : command.getSortOrder());
        StockSegment resolvedSegment = type.resolveSegment(command.getStockSegment(), entity.getStockSegment());
        entity.setStockSegment(resolvedSegment);
        entity.setIndexAsset(type.resolveIndexAsset(resolvedSegment, command.getIndexAsset(), entity.isIndexAsset()));
        entity.setCompareOnly(type.resolveCompareOnly(resolvedSegment, command.getCompareOnly(), entity.isCompareOnly()));

        TrackedAsset saved = trackedAssetRepository.save(entity);
        codeCache.invalidate(type);
        return trackedAssetMapper.toResponse(saved);
    }

    public void setEnabled(TrackedAssetType type, String assetCode, boolean enabled) {
        String normalizedCode = type.normalizeCode(assetCode);
        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
        entity.setEnabled(enabled);
        trackedAssetRepository.save(entity);
        codeCache.invalidate(type);
    }

    public void delete(TrackedAssetType type, String assetCode) {
        String normalizedCode = type.normalizeCode(assetCode);
        TrackedAsset entity = trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
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
                    .orElseThrow(() -> new ResourceNotFoundException("Tracked asset not found: " + type + " / " + normalizedCode));
            entity.setSortOrder(item.getSortOrder());
            entitiesToUpdate.add(entity);
        }

        trackedAssetRepository.saveAll(entitiesToUpdate);
        codeCache.invalidate(type);
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
}
