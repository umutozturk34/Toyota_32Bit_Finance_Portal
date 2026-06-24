package com.finance.market.fund.service;

import com.finance.market.core.service.BaseTrackedMarketAssetProvider;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.cache.MarketCacheService;



import com.finance.shared.dto.response.GroupCount;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.fund.mapper.FundResponseMapper;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundType;
import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.fund.repository.FundRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Read-side provider for TEFAS funds: supports fund-type/sub-category/risk faceting and groups
 * counts by fund type.
 */
@Log4j2
@Service
public class FundMarketAssetProvider extends BaseTrackedMarketAssetProvider<Fund> {

    private static final List<String> SEARCH_FIELDS = List.of("fundCode", "name");

    private final FundRepository fundRepository;
    private final MarketCacheService<Fund> fundCacheService;
    private final FundResponseMapper fundResponseMapper;
    private final FundDetailEnrichmentService detailEnrichmentService;
    /** Fund codes whose lazy profile enrichment has already been attempted this run, so a fund with no TEFAS
     *  profile (isinCode stays null) is not re-fetched on every detail open / focus-refetch. */
    private final java.util.Set<String> profileEnrichAttempted = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public FundMarketAssetProvider(FundRepository fundRepository,
                                   MarketCacheService<Fund> fundCacheService,
                                   FundResponseMapper fundResponseMapper,
                                   TrackedAssetQueryService trackedAssetQueryService,
                                   FundDetailEnrichmentService detailEnrichmentService) {
        super(fundRepository, trackedAssetQueryService);
        this.fundRepository = fundRepository;
        this.fundCacheService = fundCacheService;
        this.fundResponseMapper = fundResponseMapper;
        this.detailEnrichmentService = detailEnrichmentService;
    }

    @Override
    public MarketType getType() {
        return MarketType.FUND;
    }

    /**
     * Single-fund detail open. The settlement valör / ISIN / trade-window fields come from a per-fund TEFAS
     * profile call that the bulk refresh deliberately skips (it would be 2000+ HTTP calls per cycle). isinCode
     * is profile-only, so a null one means the profile has never been fetched: kick the fetch off on a
     * background thread (at most ONCE per fund per run — the {@code profileEnrichAttempted} guard) and return the
     * current snapshot immediately, so the page never blocks and a profile-less fund is not re-fetched on every
     * open. The enriched fields persist and surface on the next view/refetch.
     */
    @Override
    public MarketAssetResponse getByCode(String code) {
        Fund snapshot = getSnapshotByCode(code);
        if (snapshot == null) return null;
        // isinCode comes from the per-fund PROFILE call and category from the per-fund INFO call — the bulk
        // refresh runs neither. A null on EITHER means its TEFAS call has never landed, so kick the (info +
        // profile) fetch off in the background. Gating on isin alone left category permanently empty on funds
        // whose profile was back-filled (isin set) but whose info call never ran.
        if ((snapshot.getIsinCode() == null || snapshot.getCategory() == null) && profileEnrichAttempted.add(code)) {
            detailEnrichmentService.enrichSingleFundDetailsAsync(code);
        }
        return super.getByCode(code);
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
    protected Map<String, String> sortFields() {
        return Map.ofEntries(
                Map.entry("price", "price"),
                Map.entry("changePercent", "changePercent"),
                Map.entry("name", "name"),
                Map.entry("bulletinPrice", "bulletinPrice"),
                Map.entry("portfolioSize", "portfolioSize"),
                Map.entry("investorCount", "investorCount"),
                // TEFAS trailing returns per window — let the list rank funds by any of them.
                Map.entry("return1m", "return1m"),
                Map.entry("return3m", "return3m"),
                Map.entry("return6m", "return6m"),
                Map.entry("returnYtd", "returnYtd"),
                Map.entry("return1y", "return1y"),
                Map.entry("return3y", "return3y"),
                Map.entry("return5y", "return5y"),
                Map.entry("default", "changePercent")
        );
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
        if (filters == null) return spec;
        Specification<Fund> result = spec;
        if (filters.hasSubType()) {
            FundType fundTypeFilter = FundType.valueOf(filters.subType());
            result = result.and((root, query, cb) -> cb.equal(root.get("fundType"), fundTypeFilter));
        }
        if (filters.hasSubCategories()) {
            result = result.and((root, query, cb) -> root.get("subCategory").in(filters.subCategories()));
        }
        if (filters.hasRiskValues()) {
            result = result.and((root, query, cb) -> root.get("riskValue").in(filters.riskValues()));
        }
        return result;
    }

    @Override
    public List<GroupCount> getGroupCounts() {
        return fundRepository.countByFundType().stream()
                .map(row -> new GroupCount(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }
}
