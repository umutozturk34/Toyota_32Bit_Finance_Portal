package com.finance.market.core.service;

import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.core.model.BaseAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.shared.util.LikeSearchSpec;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.finance.market.core.service.MarketProviderHelper.applyDisplayNames;
import static com.finance.market.core.service.MarketProviderHelper.buildSort;

/**
 * Reusable {@link MarketAssetProvider} for tracked markets: scopes every query to the set of
 * enabled tracked codes, builds search/sort specs from per-market field names, and overlays
 * curated display names. Subclasses supply only the entity-specific field names and mapping.
 *
 * @param <T> the market's {@link BaseAsset} entity
 */
public abstract class BaseTrackedMarketAssetProvider<T extends BaseAsset> implements MarketAssetProvider {

    private final JpaSpecificationExecutor<T> repository;
    private final TrackedAssetQueryService trackedAssetQueryService;

    protected BaseTrackedMarketAssetProvider(JpaSpecificationExecutor<T> repository,
                                             TrackedAssetQueryService trackedAssetQueryService) {
        this.repository = repository;
        this.trackedAssetQueryService = trackedAssetQueryService;
    }

    /** Tracked-asset type whose enabled codes scope all queries. */
    protected abstract TrackedAssetType trackedAssetType();

    /** Entity attribute holding the asset code (the in-clause / tracked-set key). */
    protected abstract String codeField();

    /** Entity attributes searched by the contains filter. */
    protected abstract List<String> searchFields();

    /** Entity attribute holding change percent (drives top-movers sort/sign filter). */
    protected abstract String changePercentField();

    /** Entity attribute holding price. */
    protected abstract String priceField();

    protected String nameField() {
        return "name";
    }

    protected Map<String, String> sortFields() {
        String changePercent = changePercentField();
        return Map.of(
                "price", priceField(),
                "changePercent", changePercent,
                "name", nameField(),
                "default", changePercent
        );
    }

    protected abstract T getSnapshotByCode(String code);

    protected abstract List<MarketAssetResponse> mapToResponses(List<T> entities);

    /** Hook for subclasses to AND market-specific facet predicates; no-op by default. */
    protected Specification<T> applyCustomFilters(Specification<T> spec, MarketAssetFilters filters) {
        return spec;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        T snapshot = getSnapshotByCode(code);
        if (snapshot == null) return null;
        return withDisplayNames(mapToResponses(List.of(snapshot))).stream().findFirst().orElse(null);
    }

    @Override
    public MarketAssetResponse getByCodeIfEnabled(String code) {
        if (code == null || code.isBlank()) return null;
        String normalizedCode = trackedAssetType().normalizeCode(code);
        return enabledCodes().contains(normalizedCode) ? getByCode(normalizedCode) : null;
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters,
                                            String sortBy, String direction, int page, int size) {
        Set<String> enabledCodes = enabledCodes();
        Specification<T> spec = applyCustomFilters(buildSearchSpec(searchTerm, enabledCodes), filters);
        List<T> entities = repository.findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, sortFields(), codeField()))).getContent();
        return withDisplayNames(mapToResponses(entities));
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Set<String> enabledCodes = enabledCodes();
        Specification<T> spec = enabledCodesSpec(enabledCodes)
                .and(nonNullChangePercent())
                .and(signSpec(gainers))
                .and(topMoversAdditionalSpec());
        Sort sort = gainers
                ? Sort.by(Sort.Direction.DESC, changePercentField())
                : Sort.by(Sort.Direction.ASC, changePercentField());
        List<T> entities = repository.findAll(spec, PageRequest.of(0, limit, sort)).getContent();
        return withDisplayNames(mapToResponses(entities));
    }

    /** Extra constraint applied only to top-movers queries; matches everything by default. */
    protected Specification<T> topMoversAdditionalSpec() {
        return (root, query, cb) -> cb.conjunction();
    }

    @Override
    public long count(MarketAssetFilters filters) {
        Set<String> enabledCodes = enabledCodes();
        Specification<T> spec = applyCustomFilters(enabledCodesSpec(enabledCodes), filters);
        return repository.count(spec);
    }

    @Override
    public long countBySearch(String searchTerm, MarketAssetFilters filters) {
        Set<String> enabledCodes = enabledCodes();
        Specification<T> spec = applyCustomFilters(buildSearchSpec(searchTerm, enabledCodes), filters);
        return repository.count(spec);
    }

    private Set<String> enabledCodes() {
        return new HashSet<>(trackedAssetQueryService.getEnabledCodes(trackedAssetType()));
    }

    private Specification<T> buildSearchSpec(String searchTerm, Set<String> enabledCodes) {
        Specification<T> spec = enabledCodesSpec(enabledCodes);
        if (searchTerm == null || searchTerm.isBlank()) return spec;
        List<String> fields = searchFields();
        // Match via the unaccent-based predicate so Turkish dotted/dotless I and other diacritics fold
        // (e.g. "hisse" matches "HİSSE", "azimut" matches "AZİMUT") — the plain lower() LIKE missed them
        // because Postgres lower() and Java toLowerCase() disagree on İ/I across locales.
        return spec.and((root, query, cb) ->
                LikeSearchSpec.byFieldsContainsAllTokensUnaccent(root, cb, searchTerm, fields.toArray(new String[0])));
    }

    private Specification<T> enabledCodesSpec(Set<String> enabledCodes) {
        return (root, query, cb) -> root.get(codeField()).in(enabledCodes);
    }

    private Specification<T> nonNullChangePercent() {
        return (root, query, cb) -> cb.isNotNull(root.get(changePercentField()));
    }

    private Specification<T> signSpec(boolean gainers) {
        return (root, query, cb) -> gainers
                ? cb.greaterThan(root.get(changePercentField()), java.math.BigDecimal.ZERO)
                : cb.lessThan(root.get(changePercentField()), java.math.BigDecimal.ZERO);
    }

    private List<MarketAssetResponse> withDisplayNames(List<MarketAssetResponse> responses) {
        return applyDisplayNames(responses, trackedAssetQueryService.getDisplayNameMap(trackedAssetType()));
    }
}
