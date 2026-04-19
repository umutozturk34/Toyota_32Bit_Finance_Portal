package com.finance.backend.service;

import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.TrackedAssetMapper;
import com.finance.backend.model.TrackedAsset;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.TrackedAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackedAssetQueryService {
    private final TrackedAssetRepository trackedAssetRepository;
    private final TrackedAssetMapper trackedAssetMapper;
    private final TrackedAssetCodeCache codeCache;

    public List<TrackedAssetResponse> getTrackedAssets(TrackedAssetType type, boolean includeDisabled) {
        List<TrackedAsset> assets = includeDisabled
                ? trackedAssetRepository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type)
                : trackedAssetRepository.findByAssetTypeAndEnabledTrueOrderBySortOrderAscAssetCodeAsc(type);
        return assets.stream()
                .map(trackedAssetMapper::toResponse)
                .toList();
    }

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

    public Optional<TrackedAssetResponse> getTrackedAsset(TrackedAssetType type, String assetCode) {
        String normalizedCode = type.normalizeCode(assetCode);
        return trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode)
                .map(trackedAssetMapper::toResponse);
    }

    public void ensureEnabledOrThrow(TrackedAssetType type, String assetCode) {
        resolveEnabledCodeOrThrow(type, assetCode);
    }

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

    public List<String> getEnabledCodes(TrackedAssetType type) {
        return codeCache.get(type);
    }

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

    public Optional<String> getCryptoBinanceSymbol(String coinGeckoId) {
        String normalizedCode = TrackedAssetType.CRYPTO.normalizeCode(coinGeckoId);
        return trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, normalizedCode)
                .map(TrackedAsset::getBinanceSymbol)
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty());
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
}
