package com.finance.market.forex.service;

import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.forex.mapper.ForexResponseMapper;
import com.finance.market.forex.model.Forex;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.forex.repository.ForexRepository;
import com.finance.shared.util.LikeSearchSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.finance.market.core.service.MarketProviderHelper.buildSort;

/**
 * Read-side {@link MarketAssetProvider} for forex, scoped to tracked-enabled currencies; single-code
 * lookups read the live cache while search/movers query the repository.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ForexMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "sellingPrice",
            "changePercent", "changePercent",
            "name", "name",
            "default", "changePercent"
    );

    private final ForexRepository forexRepository;
    private final MarketCacheService<Forex> forexCacheService;
    private final ForexResponseMapper forexResponseMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;

    @Override
    public MarketType getType() {
        return MarketType.FOREX;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        Forex forex = forexCacheService.getSnapshot(code);
        if (forex == null) return null;
        return forexResponseMapper.toMarketAssetResponses(List.of(forex)).stream().findFirst().orElse(null);
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters, String sortBy, String direction, int page, int size) {
        Specification<Forex> spec = buildSpecification(searchTerm);
        List<Forex> forexList = forexRepository.findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, SORT_FIELDS, "currencyCode"))).getContent();
        return forexResponseMapper.toMarketAssetResponses(forexList);
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Specification<Forex> spec = nonNullChangePercent().and(signSpec(gainers));
        Sort sort = gainers
                ? Sort.by(Sort.Direction.DESC, "changePercent")
                : Sort.by(Sort.Direction.ASC, "changePercent");
        List<Forex> forexList = forexRepository.findAll(spec, PageRequest.of(0, limit, sort)).getContent();
        return forexResponseMapper.toMarketAssetResponses(forexList);
    }

    @Override
    public long count(MarketAssetFilters filters) {
        return forexRepository.count(enabledSpec());
    }

    @Override
    public long countBySearch(String searchTerm, MarketAssetFilters filters) {
        return forexRepository.count(buildSpecification(searchTerm));
    }

    private Specification<Forex> buildSpecification(String searchTerm) {
        Specification<Forex> spec = enabledSpec();
        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    LikeSearchSpec.byFieldsContains(root, cb, searchTerm, "currencyCode", "name"));
        }
        return spec;
    }

    private Specification<Forex> enabledSpec() {
        java.util.Set<String> enabled = new java.util.HashSet<>(
                trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FOREX));
        return (root, query, cb) -> root.get("currencyCode").in(enabled);
    }

    private Specification<Forex> nonNullChangePercent() {
        return (root, query, cb) -> cb.isNotNull(root.get("changePercent"));
    }

    private Specification<Forex> signSpec(boolean gainers) {
        return (root, query, cb) -> gainers
                ? cb.greaterThan(root.get("changePercent"), BigDecimal.ZERO)
                : cb.lessThan(root.get("changePercent"), BigDecimal.ZERO);
    }
}
