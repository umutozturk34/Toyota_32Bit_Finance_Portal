package com.finance.backend.service;

import com.finance.backend.dto.response.TrackedAssetResponse;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.TrackedAssetMapper;
import com.finance.backend.model.TrackedAsset;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.TrackedAssetRepository;
import com.finance.backend.util.LikeSearchSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

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
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        Specification<TrackedAsset> spec = buildSearchSpecification(types, includeDisabled, search);
        Sort sort = buildSort(sortBy, direction);
        return trackedAssetRepository.findAll(spec, sort).stream()
                .map(trackedAssetMapper::toResponse)
                .toList();
    }

    private Specification<TrackedAsset> buildSearchSpecification(List<TrackedAssetType> types,
                                                                  boolean includeDisabled,
                                                                  String search) {
        Specification<TrackedAsset> spec = (root, query, cb) -> root.get("assetType").in(types);
        if (!includeDisabled) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("enabled")));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    LikeSearchSpec.byFieldsContains(root, cb, search, "assetCode", "displayName"));
        }
        return spec;
    }

    private Sort buildSort(String sortBy, String direction) {
        boolean desc = "desc".equalsIgnoreCase(direction);
        return switch (sortBy != null ? sortBy : "sortOrder") {
            case "assetCode" -> Sort.by(order("assetCode", desc));
            case "displayName" -> Sort.by(withNaturalNulls(order("displayName", desc), desc));
            default -> Sort.by(
                    withNaturalNulls(order("sortOrder", desc), desc),
                    order("assetCode", desc));
        };
    }

    private Sort.Order order(String property, boolean desc) {
        return desc ? Sort.Order.desc(property) : Sort.Order.asc(property);
    }

    private Sort.Order withNaturalNulls(Sort.Order o, boolean desc) {
        return desc ? o.nullsFirst() : o.nullsLast();
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

}
