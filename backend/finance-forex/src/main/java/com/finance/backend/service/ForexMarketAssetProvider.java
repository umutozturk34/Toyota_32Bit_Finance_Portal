package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.ForexResponseMapper;
import com.finance.backend.model.Forex;
import com.finance.backend.model.MarketType;
import com.finance.backend.repository.ForexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.finance.backend.service.MarketProviderHelper.buildSort;

@Log4j2
@Service
@RequiredArgsConstructor
public class ForexMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "currentPrice",
            "changePercent", "changePercent",
            "name", "currencyName",
            "default", "changePercent"
    );

    private final ForexRepository forexRepository;
    private final MarketCacheService<Forex> forexCacheService;
    private final ForexResponseMapper forexResponseMapper;

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

        List<Forex> forexList = forexRepository.findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, SORT_FIELDS))).getContent();
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
        return forexRepository.count();
    }

    @Override
    public long countBySearch(String searchTerm, MarketAssetFilters filters) {
        return forexRepository.count(buildSpecification(searchTerm));
    }

    private Specification<Forex> buildSpecification(String searchTerm) {
        Specification<Forex> spec = (root, query, cb) -> cb.conjunction();
        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("currencyCode")), pattern),
                    cb.like(cb.lower(root.get("currencyName")), pattern)));
        }
        return spec;
    }

    private Specification<Forex> nonNullChangePercent() {
        return (root, query, cb) -> cb.isNotNull(root.get("changePercent"));
    }

    private Specification<Forex> signSpec(boolean gainers) {
        return (root, query, cb) -> gainers
                ? cb.greaterThan(root.get("changePercent"), java.math.BigDecimal.ZERO)
                : cb.lessThan(root.get("changePercent"), java.math.BigDecimal.ZERO);
    }
}
