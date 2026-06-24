package com.finance.market.stock.service;
import com.finance.market.core.service.MarketAssetProvider.MarketAssetFilters;

import com.finance.market.core.service.BaseTrackedMarketAssetProvider;

import com.finance.market.core.service.TrackedAssetQueryService;

import com.finance.market.core.cache.MarketCacheService;



import com.finance.shared.dto.response.GroupCount;
import com.finance.shared.dto.response.StockMetadata;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.stock.mapper.StockResponseMapper;
import com.finance.common.model.MarketType;
import com.finance.market.stock.model.CompanyProfile;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockIndexMembership;
import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.stock.repository.CompanyProfileRepository;
import com.finance.market.stock.repository.StockIndexMembershipRepository;
import com.finance.market.stock.repository.StockRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-side provider for BIST stocks: supports segment faceting, groups counts by segment, and
 * excludes the main-index pseudo-asset from top movers.
 */
@Log4j2
@Service
public class StockMarketAssetProvider extends BaseTrackedMarketAssetProvider<Stock> {

    private static final List<String> SEARCH_FIELDS = List.of("symbol", "name");

    private final StockRepository stockRepository;
    private final MarketCacheService<Stock> stockCacheService;
    private final StockResponseMapper stockResponseMapper;
    private final CompanyProfileRepository companyProfileRepository;
    private final StockIndexMembershipRepository membershipRepository;

    public StockMarketAssetProvider(StockRepository stockRepository,
                                    MarketCacheService<Stock> stockCacheService,
                                    StockResponseMapper stockResponseMapper,
                                    CompanyProfileRepository companyProfileRepository,
                                    StockIndexMembershipRepository membershipRepository,
                                    TrackedAssetQueryService trackedAssetQueryService) {
        super(stockRepository, trackedAssetQueryService);
        this.stockRepository = stockRepository;
        this.stockCacheService = stockCacheService;
        this.stockResponseMapper = stockResponseMapper;
        this.companyProfileRepository = companyProfileRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Single-asset detail: the base response enriched with the company künye and weighted index membership
     * (one extra profile + membership query — detail only, never on the list path). Falls back to the base
     * response when no enrichment exists yet, so a stock without a scraped profile still renders.
     */
    @Override
    public MarketAssetResponse getByCode(String code) {
        MarketAssetResponse base = super.getByCode(code);
        if (base == null) return null;
        Stock snapshot = getSnapshotByCode(code);
        if (snapshot == null) return base;
        String symbol = snapshot.getSymbol();
        CompanyProfile profile = companyProfileRepository.findById(symbol).orElse(null);
        List<StockIndexMembership> memberships = membershipRepository.findByIdStockSymbol(symbol);
        // When the asset is itself an index, also load its constituents (the reverse view: which stocks make
        // it up). Bare code without the exchange suffix, since membership stores index codes as e.g. 'XU030'.
        List<StockIndexMembership> constituents = isIndex(snapshot)
                ? membershipRepository.findByIdIndexCodeOrderByWeightDesc(stripSuffix(symbol))
                : List.of();
        if (profile == null && memberships.isEmpty() && constituents.isEmpty()) return base;
        Map<String, String> constituentNames = loadConstituentNames(constituents);
        StockMetadata detail = stockResponseMapper.buildDetailMetadata(snapshot, profile, memberships, constituents, constituentNames);
        return new MarketAssetResponse(base.code(), base.name(), base.image(), base.type(),
                base.price(), base.changeAmount(), base.changePercent(), base.lastUpdated(), detail);
    }

    /**
     * Resolves each constituent symbol to its company name in one batch query, so the index detail view can
     * show the full name on hover over the bare ticker. Empty for a normal stock (no constituents); a member
     * with no name in the stocks table simply maps to no entry and the client falls back to the symbol.
     */
    private Map<String, String> loadConstituentNames(List<StockIndexMembership> constituents) {
        if (constituents.isEmpty()) return Map.of();
        List<String> symbols = constituents.stream().map(m -> m.getId().getStockSymbol()).toList();
        return stockRepository.findAllById(symbols).stream()
                .filter(s -> s.getName() != null && !s.getName().isBlank())
                .collect(Collectors.toMap(Stock::getSymbol, Stock::getName, (a, b) -> a));
    }

    private static boolean isIndex(Stock stock) {
        return stock.getStockSegment() == StockSegment.MAIN_INDEX
                || stock.getStockSegment() == StockSegment.SECONDARY_INDEX;
    }

    private static String stripSuffix(String symbol) {
        int dot = symbol.indexOf('.');
        return dot > 0 ? symbol.substring(0, dot) : symbol;
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
    protected Map<String, String> sortFields() {
        return Map.of(
                "price", "currentPrice",
                "changePercent", "changePercent",
                "name", "name",
                "volume", "volume",
                "default", "changePercent"
        );
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
