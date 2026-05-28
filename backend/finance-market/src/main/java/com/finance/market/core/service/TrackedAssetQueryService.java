package com.finance.market.core.service;

import com.finance.market.core.dto.response.TrackedAssetResponse;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.core.mapper.TrackedAssetMapper;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.shared.util.LikeSearchSpec;
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

/**
 * Read side for the tracked-asset catalogue: listing, search/sort, code lookups (cache-backed),
 * and display-name resolution used to label market responses.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrackedAssetQueryService {
    private final TrackedAssetRepository trackedAssetRepository;
    private final TrackedAssetMapper trackedAssetMapper;
    private final TrackedAssetCodeCache codeCache;

    public List<TrackedAssetResponse> getTrackedAssets(TrackedAssetType type) {
        return trackedAssetRepository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type).stream()
                .map(trackedAssetMapper::toResponse)
                .toList();
    }

    public List<TrackedAssetResponse> searchTrackedAssets(List<TrackedAssetType> types,
                                                           String search,
                                                           String sortBy,
                                                           String direction) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        Specification<TrackedAsset> spec = buildSearchSpecification(types, search);
        Sort sort = buildSort(sortBy, direction);
        return trackedAssetRepository.findAll(spec, sort).stream()
                .map(trackedAssetMapper::toResponse)
                .toList();
    }

    private Specification<TrackedAsset> buildSearchSpecification(List<TrackedAssetType> types, String search) {
        Specification<TrackedAsset> spec = (root, query, cb) -> root.get("assetType").in(types);
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

    /**
     * Normalizes the code and confirms it is tracked, returning the canonical form.
     *
     * @throws ResourceNotFoundException when the asset is not tracked
     */
    public String resolveCodeOrThrow(TrackedAssetType type, String assetCode) {
        String normalizedCode = type.normalizeCode(assetCode);
        boolean exists = trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(type, normalizedCode).isPresent();
        if (!exists) {
            throw new ResourceNotFoundException("error.trackedAsset.notFound", type, normalizedCode);
        }
        return normalizedCode;
    }

    public List<String> getCodes(TrackedAssetType type) {
        return codeCache.get(type);
    }

    public List<String> getEnabledCodes(TrackedAssetType type) {
        return codeCache.getEnabled(type);
    }

    /** Code-to-curated-display-name map (insertion-ordered) for assets that have a non-blank name. */
    public Map<String, String> getDisplayNameMap(TrackedAssetType type) {
        return trackedAssetRepository.findByAssetTypeOrderBySortOrderAscAssetCodeAsc(type)
                .stream()
                .filter(asset -> asset.getDisplayName() != null && !asset.getDisplayName().isBlank())
                .collect(Collectors.toMap(
                        TrackedAsset::getAssetCode,
                        TrackedAsset::getDisplayName,
                        (first, second) -> second,
                        LinkedHashMap::new
                ));
    }

    /** Binance trading symbol mapped to a CoinGecko id, used to fetch klines for that coin. */
    public Optional<String> getCryptoBinanceSymbol(String coinGeckoId) {
        String normalizedCode = TrackedAssetType.CRYPTO.normalizeCode(coinGeckoId);
        return trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.CRYPTO, normalizedCode)
                .map(TrackedAsset::getBinanceSymbol)
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty());
    }

}
