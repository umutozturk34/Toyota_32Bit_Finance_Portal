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

public abstract class BaseTrackedMarketAssetProvider<T extends BaseAsset> implements MarketAssetProvider {

    private final JpaSpecificationExecutor<T> repository;
    private final TrackedAssetQueryService trackedAssetQueryService;

    protected BaseTrackedMarketAssetProvider(JpaSpecificationExecutor<T> repository,
                                             TrackedAssetQueryService trackedAssetQueryService) {
        this.repository = repository;
        this.trackedAssetQueryService = trackedAssetQueryService;
    }

    protected abstract TrackedAssetType trackedAssetType();

    protected abstract String codeField();

    protected abstract List<String> searchFields();

    protected abstract String changePercentField();

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
    public List<MarketAssetResponse> search(String searchTerm, MarketAssetFilters filters,
                                            String sortBy, String direction, int page, int size) {
        Set<String> trackedCodes = new HashSet<>(trackedAssetQueryService.getCodes(trackedAssetType()));
        Specification<T> spec = applyCustomFilters(buildSearchSpec(searchTerm, trackedCodes), filters);
        List<T> entities = repository.findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, sortFields()))).getContent();
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
        return new HashSet<>(trackedAssetQueryService.getCodes(trackedAssetType()));
    }

    private Specification<T> buildSearchSpec(String searchTerm, Set<String> enabledCodes) {
        Specification<T> spec = enabledCodesSpec(enabledCodes);
        if (searchTerm == null || searchTerm.isBlank()) return spec;
        List<String> fields = searchFields();
        return spec.and((root, query, cb) ->
                LikeSearchSpec.byFieldsContains(root, cb, searchTerm, fields));
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
