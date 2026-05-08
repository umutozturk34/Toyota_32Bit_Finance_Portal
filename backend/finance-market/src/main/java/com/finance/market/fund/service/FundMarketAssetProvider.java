package com.finance.market.fund.service;
import com.finance.common.service.MarketAssetProvider.MarketAssetFilters;

import com.finance.common.service.BaseTrackedMarketAssetProvider;

import com.finance.common.service.TrackedAssetQueryService;

import com.finance.cache.service.MarketCacheService;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.common.dto.response.GroupCount;
import com.finance.common.dto.response.MarketAssetResponse;
import com.finance.market.fund.mapper.FundResponseMapper;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import com.finance.market.fund.model.FundType;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.fund.repository.FundRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
public class FundMarketAssetProvider extends BaseTrackedMarketAssetProvider<Fund> {

    private static final List<String> SEARCH_FIELDS = List.of("fundCode", "name");

    private final FundRepository fundRepository;
    private final MarketCacheService<Fund> fundCacheService;
    private final FundResponseMapper fundResponseMapper;

    public FundMarketAssetProvider(FundRepository fundRepository,
                                   MarketCacheService<Fund> fundCacheService,
                                   FundResponseMapper fundResponseMapper,
                                   TrackedAssetQueryService trackedAssetQueryService) {
        super(fundRepository, trackedAssetQueryService);
        this.fundRepository = fundRepository;
        this.fundCacheService = fundCacheService;
        this.fundResponseMapper = fundResponseMapper;
    }

    @Override
    public MarketType getType() {
        return MarketType.FUND;
    }

    @Override
    protected TrackedAssetType trackedAssetType() {
        return TrackedAssetType.FUND;
    }

    @Override
    protected String codeField() {
        return "fundCode";
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
        return "price";
    }

    @Override
    protected Fund getSnapshotByCode(String code) {
        return fundCacheService.getSnapshot(code);
    }

    @Override
    protected List<MarketAssetResponse> mapToResponses(List<Fund> entities) {
        return fundResponseMapper.toMarketAssetResponses(entities);
    }

    @Override
    protected Specification<Fund> applyCustomFilters(Specification<Fund> spec, MarketAssetFilters filters) {
        if (filters == null || !filters.hasSubType()) return spec;
        FundType fundTypeFilter = FundType.valueOf(filters.subType());
        return spec.and((root, query, cb) -> cb.equal(root.get("fundType"), fundTypeFilter));
    }

    @Override
    public List<GroupCount> getGroupCounts() {
        return fundRepository.countByFundType().stream()
                .map(row -> new GroupCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }
}
