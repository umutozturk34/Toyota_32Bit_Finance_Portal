package com.finance.market.stock.service;
import com.finance.market.core.service.MarketAssetProvider.MarketAssetFilters;

import com.finance.market.core.service.BaseTrackedMarketAssetProvider;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.cache.MarketCacheService;



import com.finance.shared.dto.response.GroupCount;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.stock.mapper.StockResponseMapper;
import com.finance.common.model.MarketType;
import com.finance.market.stock.model.Stock;
import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.stock.repository.StockRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
public class StockMarketAssetProvider extends BaseTrackedMarketAssetProvider<Stock> {

    private static final List<String> SEARCH_FIELDS = List.of("symbol", "name");

    private final StockRepository stockRepository;
    private final MarketCacheService<Stock> stockCacheService;
    private final StockResponseMapper stockResponseMapper;

    public StockMarketAssetProvider(StockRepository stockRepository,
                                    MarketCacheService<Stock> stockCacheService,
                                    StockResponseMapper stockResponseMapper,
                                    TrackedAssetQueryService trackedAssetQueryService) {
        super(stockRepository, trackedAssetQueryService);
        this.stockRepository = stockRepository;
        this.stockCacheService = stockCacheService;
        this.stockResponseMapper = stockResponseMapper;
    }

    @Override
    public MarketType getType() {
        return MarketType.STOCK;
    }

    @Override
    protected TrackedAssetType trackedAssetType() {
        return TrackedAssetType.STOCK;
    }

    @Override
    protected String codeField() {
        return "symbol";
    }

    @Override
    protected List<String> searchFields() {
        return SEARCH_FIELDS;
    }

    @Override
    protected String changePercentField() {
        return "changePercent";
    }

    @Override
    protected String priceField() {
        return "currentPrice";
    }

    @Override
    protected Stock getSnapshotByCode(String code) {
        return stockCacheService.getSnapshot(code);
    }

    @Override
    protected List<MarketAssetResponse> mapToResponses(List<Stock> entities) {
        return stockResponseMapper.toMarketAssetResponses(entities);
    }

    @Override
    protected Specification<Stock> applyCustomFilters(Specification<Stock> spec, MarketAssetFilters filters) {
        if (filters == null || !filters.hasSegment()) return spec;
        StockSegment segmentValue = StockSegment.valueOf(filters.segment());
        return spec.and((root, query, cb) -> cb.equal(root.get("stockSegment"), segmentValue));
    }

    @Override
    protected Specification<Stock> topMoversAdditionalSpec() {
        return (root, query, cb) -> cb.notEqual(root.get("stockSegment"), StockSegment.MAIN_INDEX);
    }

    @Override
    public List<GroupCount> getGroupCounts() {
        return stockRepository.countBySegment().stream()
                .map(row -> new GroupCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }
}
