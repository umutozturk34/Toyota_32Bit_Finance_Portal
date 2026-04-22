package com.finance.backend.service;

import com.finance.backend.dto.response.GroupCount;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.CommodityResponseMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.CommoditySegment;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
public class CommodityMarketAssetProvider extends BaseTrackedMarketAssetProvider<Commodity> {

    private static final List<String> SEARCH_FIELDS = List.of("commodityCode", "commodityName", "commodityNameTr", "name");

    private final CommodityRepository commodityRepository;
    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final CommodityResponseMapper commodityResponseMapper;

    public CommodityMarketAssetProvider(CommodityRepository commodityRepository,
                                        MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                        CommodityResponseMapper commodityResponseMapper,
                                        TrackedAssetQueryService trackedAssetQueryService) {
        super(commodityRepository, trackedAssetQueryService);
        this.commodityRepository = commodityRepository;
        this.commodityCacheService = commodityCacheService;
        this.commodityResponseMapper = commodityResponseMapper;
    }

    @Override
    public MarketType getType() {
        return MarketType.COMMODITY;
    }

    @Override
    protected TrackedAssetType trackedAssetType() {
        return TrackedAssetType.COMMODITY;
    }

    @Override
    protected String codeField() {
        return "commodityCode";
    }

    @Override
    protected List<String> searchFields() {
        return SEARCH_FIELDS;
    }

    @Override
    protected String changePercentField() {
        return "changePercent24h";
    }

    @Override
    protected String priceField() {
        return "currentPrice";
    }

    @Override
    protected Commodity getSnapshotByCode(String code) {
        return commodityCacheService.getSnapshot(code);
    }

    @Override
    protected List<MarketAssetResponse> mapToResponses(List<Commodity> entities) {
        return commodityResponseMapper.toMarketAssetResponses(entities);
    }

    @Override
    protected Specification<Commodity> applyCustomFilters(Specification<Commodity> spec, MarketAssetFilters filters) {
        if (filters == null || !filters.hasSegment()) return spec;
        CommoditySegment segmentValue = CommoditySegment.valueOf(filters.segment());
        return spec.and((root, query, cb) -> cb.equal(root.get("commoditySegment"), segmentValue));
    }

    @Override
    public List<GroupCount> getGroupCounts() {
        return commodityRepository.countBySegment().stream()
                .map(row -> new GroupCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }
}
