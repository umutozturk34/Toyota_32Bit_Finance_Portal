package com.finance.backend.service;

import com.finance.backend.dto.response.GroupCount;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.StockResponseMapper;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.Stock;
import com.finance.backend.model.StockCandle;
import com.finance.backend.model.StockSegment;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.StockRepository;
import com.finance.backend.service.MarketAssetProvider.MarketAssetFilters;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class StockMarketAssetProvider extends BaseTrackedMarketAssetProvider<Stock> {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "currentPrice",
            "changePercent", "priceChangePercent",
            "name", "name",
            "default", "priceChangePercent"
    );
    private static final List<String> SEARCH_FIELDS = List.of("symbol", "name");

    private final StockRepository stockRepository;
    private final MarketCacheService<Stock, StockCandle> stockCacheService;
    private final StockResponseMapper stockResponseMapper;

    public StockMarketAssetProvider(StockRepository stockRepository,
                                    MarketCacheService<Stock, StockCandle> stockCacheService,
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
        return "priceChangePercent";
    }

    @Override
    protected Map<String, String> sortFields() {
        return SORT_FIELDS;
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
    public List<GroupCount> getGroupCounts() {
        return stockRepository.countBySegment().stream()
                .map(row -> new GroupCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }
}
