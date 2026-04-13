package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.StockResponseMapper;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.model.StockSegment;
import com.finance.backend.repository.StockRepository;
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
public class StockMarketAssetProvider implements MarketAssetProvider {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "currentPrice",
            "changePercent", "priceChangePercent",
            "name", "name",
            "default", "priceChangePercent"
    );

    private final StockRepository stockRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final StockResponseMapper stockResponseMapper;
    private final TrackedAssetService trackedAssetService;

    @Override
    public MarketType getType() {
        return MarketType.STOCK;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        Stock stock = stockCacheService.getSnapshot(code);
        if (stock == null) return null;
        return withDisplayNames(stockResponseMapper.toMarketAssetResponses(List.of(stock))).stream().findFirst().orElse(null);
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, Map<String, String> filters, String sortBy, String direction, int page, int size) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        Specification<Stock> spec = buildSpecification(searchTerm, enabledCodes);
        String segment = filters != null ? filters.get("segment") : null;
        if (segment != null && !segment.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("stockSegment"),
                    StockSegment.valueOf(segment)));
        }
        List<Stock> stocks = stockRepository.findAll(spec, PageRequest.of(page, size, buildSort(sortBy, direction, SORT_FIELDS))).getContent();
        return withDisplayNames(stockResponseMapper.toMarketAssetResponses(stocks));
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        Specification<Stock> spec = enabledCodesSpec(enabledCodes)
                .and(nonNullChangePercent());

        var sort = gainers
                ? Sort.by(Sort.Direction.DESC, "priceChangePercent")
                : Sort.by(Sort.Direction.ASC, "priceChangePercent");

        List<Stock> stocks = stockRepository.findAll(spec, PageRequest.of(0, limit, sort)).getContent();
        return withDisplayNames(stockResponseMapper.toMarketAssetResponses(stocks));
    }

    @Override
    public long count(Map<String, String> filters) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        Specification<Stock> spec = enabledCodesSpec(enabledCodes);
        String segment = filters != null ? filters.get("segment") : null;
        if (segment != null && !segment.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("stockSegment"),
                    StockSegment.valueOf(segment)));
        }
        return stockRepository.count(spec);
    }

    @Override
    public long countBySearch(String searchTerm, Map<String, String> filters) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        Specification<Stock> spec = buildSpecification(searchTerm, enabledCodes);
        String segment = filters != null ? filters.get("segment") : null;
        if (segment != null && !segment.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("stockSegment"),
                    StockSegment.valueOf(segment)));
        }
        return stockRepository.count(spec);
    }

    @Override
    public List<Map<String, Object>> getGroupCounts() {
        return stockRepository.countBySegment().stream()
                .map(row -> Map.<String, Object>of("type", row[0].toString(), "count", row[1]))
                .toList();
    }

    private Specification<Stock> buildSpecification(String searchTerm, Set<String> enabledCodes) {
         Specification<Stock> spec = enabledCodesSpec(enabledCodes);
        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("symbol")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern)));
        }
        return spec;
    }

    private Specification<Stock> enabledCodesSpec(Set<String> enabledCodes) {
        return (root, query, cb) -> root.get("symbol").in(enabledCodes);
    }

    private Specification<Stock> nonNullChangePercent() {
        return (root, query, cb) -> cb.isNotNull(root.get("priceChangePercent"));
    }

    private List<MarketAssetResponse> withDisplayNames(List<MarketAssetResponse> responses) {
        return applyDisplayNames(responses, trackedAssetService.getEnabledDisplayNameMap(TrackedAssetType.STOCK));
    }
}
