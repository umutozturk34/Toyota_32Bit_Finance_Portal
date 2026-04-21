package com.finance.backend.service;

import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.CommodityResponseMapper;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.CommodityCandle;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.CommodityRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class CommodityMarketAssetProvider extends BaseTrackedMarketAssetProvider<Commodity> {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "currentPrice",
            "changePercent", "changePercent24h",
            "name", "name",
            "default", "changePercent24h"
    );
    private static final List<String> SEARCH_FIELDS = List.of("commodityCode", "commodityName", "commodityNameTr", "name");

    private final MarketCacheService<Commodity, CommodityCandle> commodityCacheService;
    private final CommodityResponseMapper commodityResponseMapper;

    public CommodityMarketAssetProvider(CommodityRepository commodityRepository,
                                        MarketCacheService<Commodity, CommodityCandle> commodityCacheService,
                                        CommodityResponseMapper commodityResponseMapper,
                                        TrackedAssetQueryService trackedAssetQueryService) {
        super(commodityRepository, trackedAssetQueryService);
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
    protected Map<String, String> sortFields() {
        return SORT_FIELDS;
    }

    @Override
    protected Commodity getSnapshotByCode(String code) {
        return commodityCacheService.getSnapshot(code);
    }

    @Override
    protected List<MarketAssetResponse> mapToResponses(List<Commodity> entities) {
        return commodityResponseMapper.toMarketAssetResponses(entities);
    }
}
