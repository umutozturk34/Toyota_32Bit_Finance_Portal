package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.FundResponseMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.finance.backend.service.MarketProviderHelper.applyDisplayNames;
import static com.finance.backend.service.MarketProviderHelper.buildSort;

@Log4j2
@Service
@RequiredArgsConstructor
public class FundMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "price",
            "changePercent", "changePercent",
            "name", "name",
            "default", "changePercent"
    );

    private final FundRepository fundRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final FundResponseMapper fundResponseMapper;
    private final TrackedAssetService trackedAssetService;

    @Override
    public MarketType getType() {
        return MarketType.FUND;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        Fund fund = fundCacheService.getSnapshot(code);
        if (fund == null) return null;
        return withDisplayNames(fundResponseMapper.toMarketAssetResponses(List.of(fund))).stream().findFirst().orElse(null);
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, String sortBy, String direction, int page, int size) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.FUND));
        Specification<Fund> spec = buildSpecification(searchTerm, enabledCodes);

        List<Fund> funds = fundRepository.findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, SORT_FIELDS))).getContent();
        return withDisplayNames(fundResponseMapper.toMarketAssetResponses(funds));
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.FUND));
        Specification<Fund> spec = enabledCodesSpec(enabledCodes)
                .and(nonNullChangePercent());

        Sort sort = gainers
                ? Sort.by(Sort.Direction.DESC, "changePercent")
                : Sort.by(Sort.Direction.ASC, "changePercent");

        List<Fund> funds = fundRepository.findAll(spec, PageRequest.of(0, limit, sort)).getContent();
        return withDisplayNames(fundResponseMapper.toMarketAssetResponses(funds));
    }

    @Override
    public long count() {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.FUND));
        return fundRepository.count(enabledCodesSpec(enabledCodes));
    }

    @Override
    public long countBySearch(String searchTerm) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.FUND));
        return fundRepository.count(buildSpecification(searchTerm, enabledCodes));
    }

    @Override
    public List<Map<String, Object>> getGroupCounts() {
        return fundRepository.countByFundType().stream()
                .map(row -> Map.<String, Object>of("type", row[0].toString(), "count", row[1]))
                .toList();
    }

    private Specification<Fund> buildSpecification(String searchTerm, Set<String> enabledCodes) {
        Specification<Fund> spec = enabledCodesSpec(enabledCodes);
        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fundCode")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern)));
        }
        return spec;
    }

    private Specification<Fund> enabledCodesSpec(Set<String> enabledCodes) {
        return (root, query, cb) -> root.get("fundCode").in(enabledCodes);
    }

    private Specification<Fund> nonNullChangePercent() {
        return (root, query, cb) -> cb.isNotNull(root.get("changePercent"));
    }

    private List<MarketAssetResponse> withDisplayNames(List<MarketAssetResponse> responses) {
        return applyDisplayNames(responses, trackedAssetService.getEnabledDisplayNameMap(TrackedAssetType.FUND));
    }
}
