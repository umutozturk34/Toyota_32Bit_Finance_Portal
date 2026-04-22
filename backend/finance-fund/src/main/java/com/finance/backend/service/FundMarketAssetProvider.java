package com.finance.backend.service;

import com.finance.backend.dto.response.GroupCount;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.mapper.FundResponseMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class FundMarketAssetProvider extends BaseTrackedMarketAssetProvider<Fund> {

    private static final Map<String, String> SORT_FIELDS = Map.of(
            "price", "price",
            "changePercent", "changePercent",
            "name", "name",
            "default", "changePercent"
    );
    private static final List<String> SEARCH_FIELDS = List.of("fundCode", "name");

    private final FundRepository fundRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final FundResponseMapper fundResponseMapper;

    public FundMarketAssetProvider(FundRepository fundRepository,
                                   MarketCacheService<Fund, FundCandle> fundCacheService,
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
    protected Map<String, String> sortFields() {
        return SORT_FIELDS;
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
