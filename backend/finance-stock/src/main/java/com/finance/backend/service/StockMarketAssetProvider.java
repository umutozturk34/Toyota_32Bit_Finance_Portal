package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.StockResponseMapper;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.TrackedAssetType;
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

@Log4j2
@Service
@RequiredArgsConstructor
public class StockMarketAssetProvider implements MarketAssetProvider {

    private final StockRepository stockRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final StockResponseMapper stockResponseMapper;
    private final TrackedAssetService trackedAssetService;
    private final TrackedMarketViewService trackedMarketViewService;

    @Override
    public MarketType getType() {
        return MarketType.STOCK;
    }

    @Override
    public MarketAssetResponse getByCode(String code) {
        Stock stock = stockCacheService.getSnapshot(code);
        if (stock == null) return null;
        return applyDisplayNames(stockResponseMapper.toMarketAssetResponses(List.of(stock))).stream().findFirst().orElse(null);
    }

    @Override
    public List<MarketAssetResponse> getAll() {
        List<Stock> ordered = trackedMarketViewService.getEnabledAndOrdered(
                TrackedAssetType.STOCK, stockCacheService.getAllSnapshots(), Stock::getSymbol);
        return applyDisplayNames(stockResponseMapper.toMarketAssetResponses(ordered));
    }

    @Override
    public List<MarketAssetResponse> search(String searchTerm, String sortBy, String direction, int page, int size) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        Specification<Stock> spec = buildSpecification(searchTerm, enabledCodes);
        Sort sort = buildSort(sortBy, direction);

        List<Stock> stocks = stockRepository.findAll(spec, PageRequest.of(page, size, sort)).getContent();
        return applyDisplayNames(stockResponseMapper.toMarketAssetResponses(stocks));
    }

    @Override
    public List<MarketAssetResponse> getTopMovers(int limit, boolean gainers) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        Specification<Stock> spec = enabledCodesSpec(enabledCodes)
                .and(nonNullChangePercent());

        Sort sort = gainers
                ? Sort.by(Sort.Direction.DESC, "priceChangePercent")
                : Sort.by(Sort.Direction.ASC, "priceChangePercent");

        List<Stock> stocks = stockRepository.findAll(spec, PageRequest.of(0, limit, sort)).getContent();
        return applyDisplayNames(stockResponseMapper.toMarketAssetResponses(stocks));
    }

    @Override
    public long count() {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        return stockRepository.count(enabledCodesSpec(enabledCodes));
    }

    @Override
    public long countBySearch(String searchTerm) {
        Set<String> enabledCodes = new HashSet<>(trackedAssetService.getEnabledCodes(TrackedAssetType.STOCK));
        return stockRepository.count(buildSpecification(searchTerm, enabledCodes));
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

    private Sort buildSort(String sortBy, String direction) {
        String field = switch (sortBy) {
            case "price" -> "currentPrice";
            case "changePercent" -> "priceChangePercent";
            case "name" -> "name";
            default -> "priceChangePercent";
        };
        return Sort.by("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, field);
    }

    private List<MarketAssetResponse> applyDisplayNames(List<MarketAssetResponse> responses) {
        Map<String, String> displayNameMap = trackedAssetService.getEnabledDisplayNameMap(TrackedAssetType.STOCK);
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
