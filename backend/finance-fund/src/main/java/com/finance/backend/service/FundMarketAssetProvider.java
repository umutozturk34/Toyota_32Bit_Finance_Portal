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

@Log4j2
@Service
@RequiredArgsConstructor
public class FundMarketAssetProvider implements MarketAssetProvider {

    private final FundRepository fundRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final FundResponseMapper fundResponseMapper;
    private final TrackedAssetService trackedAssetService;
    private final TrackedMarketViewService trackedMarketViewService;

    @Override
    public MarketType getType() {
        return MarketType.FUND;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        Fund fund = fundCacheService.getSnapshot(code);
        if (fund == null) return null;
        return applyDisplayNames(fundResponseMapper.toMarketAssetResponses(List.of(fund))).stream().findFirst().orElse(null);
    }

    @Override
    public List<MarketAssetResponse> getAll() {
        List<Fund> ordered = trackedMarketViewService.getEnabledAndOrdered(
                TrackedAssetType.FUND, fundCacheService.getAllSnapshots(), Fund::getFundCode);
        return applyDisplayNames(fundResponseMapper.toMarketAssetResponses(ordered));
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, String sortBy, String direction, int page, int size) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.FUND));
        Specification<Fund> spec = buildSpecification(searchTerm, enabledCodes);
        Sort sort = buildSort(sortBy, direction);

        List<Fund> funds = fundRepository.findAll(spec, PageRequest.of(page, size, sort)).getContent();
        return applyDisplayNames(fundResponseMapper.toMarketAssetResponses(funds));
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
        return applyDisplayNames(fundResponseMapper.toMarketAssetResponses(funds));
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

    private Sort buildSort(String sortBy, String direction) {
        String field = switch (sortBy) {
            case "price" -> "price";
            case "changePercent" -> "changePercent";
            case "name" -> "name";
            default -> "changePercent";
        };
        return Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, field);
    }

    private List<MarketAssetResponse> applyDisplayNames(List<MarketAssetResponse> responses) {
        Map<String, String> displayNameMap = trackedAssetService.getEnabledDisplayNameMap(TrackedAssetType.FUND);
        return responses.stream()
                .map(r -> {
                    String displayName = displayNameMap.get(r.code());
                    if (displayName == null || displayName.isBlank()) return r;
                    return new MarketAssetResponse(r.code(), displayName, r.image(), r.type(),
                            r.price(), r.changeAmount(), r.changePercent(), r.lastUpdated(), r.metadata());
                })
                .toList();
    }
}
