package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.service.MarketAssetProvider;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.viop.mapper.ViopMarketResponseMapper;
import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.shared.dto.response.GroupCount;
import com.finance.shared.util.LikeSearchSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.finance.market.core.service.MarketProviderHelper.buildSort;

/**
 * Read-side {@link MarketAssetProvider} for VIOP. Always restricts to active, non-expired,
 * tracked-enabled contracts; supports kind/category facets and groups counts by category.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ViopMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "lastPrice",
            "changePercent", "changePercent",
            "name", "name",
            "volume", "volumeLot",
            "expiryDate", "expiryDate",
            "initialMargin", "initialMargin",
            "default", "changePercent"
    );

    private final ViopContractRepository repository;
    private final ViopMarketResponseMapper responseMapper;
    private final TrackedAssetQueryService trackedAssetQueryService;

    @Override
    public MarketType getType() {
        return MarketType.VIOP;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        return repository.findBySymbol(code)
                .map(responseMapper::toResponse)
                .orElse(null);
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters,
                                            String sortBy, String direction, int page, int size) {
        Specification<ViopContract> spec = buildSearchSpec(searchTerm, filters);
        List<ViopContract> contracts = repository
                .findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, SORT_FIELDS)))
                .getContent();
        return responseMapper.toResponses(contracts);
    }

    @Override
    public long count(MarketAssetFilters filters) {
        return repository.count(buildSearchSpec(null, filters));
    }

    @Override
    public long countBySearch(String searchTerm, MarketAssetFilters filters) {
        return repository.count(buildSearchSpec(searchTerm, filters));
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Specification<ViopContract> spec = activeNonExpired()
                .and(nonNullChangePercent())
                .and(signSpec(gainers));
        Sort sort = gainers
                ? Sort.by(Sort.Direction.DESC, "changePercent")
                : Sort.by(Sort.Direction.ASC, "changePercent");
        List<ViopContract> contracts = repository
                .findAll(spec, PageRequest.of(0, limit, sort))
                .getContent();
        return responseMapper.toResponses(contracts);
    }

    @Override
    public List<GroupCount> getGroupCounts() {
        List<ViopContract> active = repository.findAll(activeNonExpired());
        Map<ViopCategory, Long> grouped = active.stream()
                .filter(c -> c.getCategory() != null)
                .collect(Collectors.groupingBy(ViopContract::getCategory, Collectors.counting()));
        return grouped.entrySet().stream()
                .map(e -> new GroupCount(e.getKey().name(), e.getValue()))
                .toList();
    }

    private Specification<ViopContract> buildSearchSpec(String searchTerm, MarketAssetFilters filters) {
        Specification<ViopContract> spec = activeNonExpired().and(enabledSpec());
        if (filters != null && filters.hasSegment()) {
            spec = spec.and(kindSpec(filters.segment()));
        }
        if (filters != null && filters.hasSubType()) {
            spec = spec.and(categorySpec(filters.subType()));
        }
        if (searchTerm != null && !searchTerm.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    LikeSearchSpec.byFieldsContains(root, cb, searchTerm, "symbol", "name", "underlying", "displayName"));
        }
        return spec;
    }

    private Specification<ViopContract> enabledSpec() {
        java.util.Set<String> enabled = new java.util.HashSet<>(
                trackedAssetQueryService.getEnabledCodes(TrackedAssetType.VIOP));
        return (root, query, cb) -> root.get("symbol").in(enabled);
    }

    /** Active contracts whose expiry is null or today-or-later (excludes matured contracts). */
    private Specification<ViopContract> activeNonExpired() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("active")),
                cb.or(cb.isNull(root.get("expiryDate")),
                        cb.greaterThanOrEqualTo(root.get("expiryDate"), LocalDate.now()))
        );
    }

    private Specification<ViopContract> kindSpec(String raw) {
        return (root, query, cb) -> {
            try {
                return cb.equal(root.get("kind"), ViopContractKind.valueOf(raw.toUpperCase()));
            } catch (IllegalArgumentException e) {
                return cb.disjunction();
            }
        };
    }

    private Specification<ViopContract> categorySpec(String raw) {
        List<ViopCategory> categories = ViopCategory.resolveFilter(raw);
        if (categories.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("category").in(categories);
    }

    private Specification<ViopContract> nonNullChangePercent() {
        return (root, query, cb) -> cb.isNotNull(root.get("changePercent"));
    }

    private Specification<ViopContract> signSpec(boolean gainers) {
        return (root, query, cb) -> gainers
                ? cb.greaterThan(root.get("changePercent"), BigDecimal.ZERO)
                : cb.lessThan(root.get("changePercent"), BigDecimal.ZERO);
    }
}
