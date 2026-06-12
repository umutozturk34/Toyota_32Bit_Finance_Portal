package com.finance.portfolio.service;

import com.finance.portfolio.service.performance.PortfolioPerformanceService;
import com.finance.portfolio.service.summary.PortfolioSummaryService;

import com.finance.common.dto.response.PagedResponse;
import com.finance.common.exception.MarketDataNotReadyException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.market.MarketDataReadiness;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.AssetAggregateResponse;
import com.finance.portfolio.dto.response.LotLimitsResponse;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PortfolioViewResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Single entry point for the portfolio controller: enforces owner access on each call and delegates
 * to the CRUD, summary and performance services. Also assembles the combined {@code view} (summary +
 * positions + allocation) and routes chart requests.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioFacade {

    private static final String INCLUDE_SUMMARY = "summary";
    private static final String INCLUDE_POSITIONS = "positions";
    private static final String INCLUDE_ALLOCATION = "allocation";
    private static final String ALLOCATION_MODE_ASSET_TYPE = "assetType";
    private static final String CHART_TYPE_ASSET_SERIES = "asset-series";
    private static final int FIRST_PAGE = 0;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioCrudService crudService;
    private final PortfolioSummaryService summaryService;
    private final PortfolioPerformanceService performanceService;
    private final PortfolioProperties portfolioProperties;
    private final com.finance.market.viop.config.ViopProperties viopProperties;

    // Optional: absent outside the full app context (e.g. unit tests), where the gate is a no-op. When present
    // (the market-data initializer), blocks position creation until the cold-start price/FX load has finished,
    // so a fresh-DB user gets a clean "still loading" 503 instead of a position that later fails to value.
    private final ObjectProvider<MarketDataReadiness> marketDataReadiness;

    /** @throws MarketDataNotReadyException (HTTP 503) if the cold-start market-data load has not finished yet. */
    private void requireMarketDataReady() {
        MarketDataReadiness readiness = marketDataReadiness.getIfAvailable();
        if (readiness != null && !readiness.isReady()) {
            throw new MarketDataNotReadyException("error.market.dataNotReady");
        }
    }

    public LotLimitsResponse getLotLimits() {
        LotLimits limits = portfolioProperties.getLotLimits();
        // VIOP candle history only spans the last max-history-years, so its entry floor is later than the
        // spot one (EUR's 1999) — clamp the VIOP date picker to where VIOP data actually starts.
        LocalDate viopMinEntryDate = LocalDate.now().minusYears(viopProperties.maxHistoryYears());
        return new LotLimitsResponse(
                limits.getMinEntryDate(),
                viopMinEntryDate,
                LocalDate.now(),
                limits.getMinPriceTry(),
                limits.getMaxPriceTry(),
                limits.getMinQuantity(),
                limits.getMaxQuantity()
        );
    }

    public List<PortfolioResponse> listPortfolios(String userSub) {
        return crudService.listPortfolios(userSub);
    }

    public PortfolioResponse createPortfolio(String userSub, PortfolioCreateRequest request) {
        return crudService.createPortfolio(userSub, request);
    }

    public PositionResponse addPosition(String userSub, Long portfolioId, PositionRequest request) {
        requireMarketDataReady();
        return crudService.addPosition(portfolioId, userSub, request);
    }

    public PositionResponse updatePosition(String userSub, Long portfolioId, Long positionId, PositionRequest request) {
        return crudService.updatePosition(portfolioId, positionId, userSub, request);
    }

    public void deletePosition(String userSub, Long portfolioId, Long positionId) {
        crudService.deletePosition(portfolioId, positionId, userSub);
    }

    public void bulkDeletePositions(String userSub, Long portfolioId, List<Long> positionIds) {
        crudService.deletePositions(portfolioId, positionIds, userSub);
    }

    public PositionResponse sellPosition(String userSub, Long portfolioId, Long positionId, PositionSellRequest request) {
        return crudService.sellPosition(portfolioId, positionId, userSub, request);
    }

    public PositionResponse reopenPosition(String userSub, Long portfolioId, Long positionId) {
        return crudService.reopenPosition(portfolioId, positionId, userSub);
    }

    public PortfolioResponse renamePortfolio(String userSub, Long portfolioId, PortfolioCreateRequest request) {
        return crudService.renamePortfolio(userSub, portfolioId, request);
    }

    public void deletePortfolio(String userSub, Long portfolioId) {
        crudService.deletePortfolio(userSub, portfolioId);
    }

    public AssetAggregateResponse getAssetAggregate(
            String userSub, Long portfolioId, String assetType, String assetCode, String direction) {
        validateOwner(userSub, portfolioId);
        return summaryService.getAssetAggregate(portfolioId, assetType, assetCode, direction);
    }

    public PagedResponse<PositionResponse> getPositionsPaged(String userSub, Long portfolioId,
                                                               String search, String assetType, String sortBy,
                                                               String direction, Boolean closed,
                                                               int page, int size) {
        validateOwner(userSub, portfolioId);
        return summaryService.getPositionsPaged(portfolioId, search, assetType, sortBy, direction, closed, page, size);
    }

    public List<PositionResponse> getPositionsByAsset(String userSub, Long portfolioId,
                                                      String assetType, String assetCode) {
        validateOwner(userSub, portfolioId);
        return summaryService.getPositionsByAsset(portfolioId, assetType, assetCode);
    }

    public PortfolioSummaryResponse getSummary(String userSub, Long portfolioId, String assetType) {
        validateOwner(userSub, portfolioId);
        return summaryService.getSummary(portfolioId, assetType);
    }

    public List<AllocationItem> getAllocation(String userSub, Long portfolioId, String mode,
                                              String assetType, Integer limit) {
        validateOwner(userSub, portfolioId);
        return summaryService.getAllocation(portfolioId, mode, assetType, limit);
    }

    /** Builds the composite portfolio view, computing only the sections named in {@code includes} (summary/positions/allocation). */
    public PortfolioViewResponse getPortfolioView(String userSub, Long portfolioId, Set<String> includes) {
        validateOwner(userSub, portfolioId);

        PortfolioSummaryResponse summary = includes.contains(INCLUDE_SUMMARY)
                ? summaryService.getSummary(portfolioId, null) : null;

        PagedResponse<PositionResponse> positions = includes.contains(INCLUDE_POSITIONS)
                ? summaryService.getPositionsPaged(portfolioId, null, null, null, null, null,
                        FIRST_PAGE, portfolioProperties.getView().getPositionPageSize()) : null;

        List<AllocationItem> allocation = includes.contains(INCLUDE_ALLOCATION)
                ? summaryService.getAllocation(portfolioId, ALLOCATION_MODE_ASSET_TYPE, null, null) : null;

        return new PortfolioViewResponse(summary, positions, allocation);
    }

    /** Routes a chart request: {@code asset-series} returns a single asset's series (optionally scoped to a
     *  LONG/SHORT direction for VIOP), otherwise the portfolio performance series. */
    public Object getChart(String userSub, Long portfolioId, String type,
                           String range, String assetType, String assetCode, String direction) {
        validateOwner(userSub, portfolioId);
        if (CHART_TYPE_ASSET_SERIES.equals(type)) {
            return performanceService.getAssetSeries(portfolioId, assetType, assetCode, range, direction);
        }
        return performanceService.getPerformance(portfolioId, range, assetType);
    }

    public void requireOwnership(String userSub, Long portfolioId) {
        validateOwner(userSub, portfolioId);
    }

    private void validateOwner(String userSub, Long portfolioId) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
    }
}
