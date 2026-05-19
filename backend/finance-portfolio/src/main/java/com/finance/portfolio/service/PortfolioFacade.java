package com.finance.portfolio.service;

import com.finance.common.dto.response.PagedResponse;
import com.finance.common.exception.ResourceNotFoundException;
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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    public LotLimitsResponse getLotLimits() {
        LotLimits limits = portfolioProperties.getLotLimits();
        return new LotLimitsResponse(
                limits.getMinEntryDate(),
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
        return crudService.addPosition(portfolioId, userSub, request);
    }

    public PositionResponse updatePosition(String userSub, Long portfolioId, Long positionId, PositionRequest request) {
        return crudService.updatePosition(portfolioId, positionId, userSub, request);
    }

    public void deletePosition(String userSub, Long portfolioId, Long positionId) {
        crudService.deletePosition(portfolioId, positionId, userSub);
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
            String userSub, Long portfolioId, String assetType, String assetCode) {
        validateOwner(userSub, portfolioId);
        return summaryService.getAssetAggregate(portfolioId, assetType, assetCode);
    }

    public PagedResponse<PositionResponse> getPositionsPaged(String userSub, Long portfolioId,
                                                               String search, String assetType, String sortBy,
                                                               String direction, int page, int size) {
        validateOwner(userSub, portfolioId);
        return summaryService.getPositionsPaged(portfolioId, search, assetType, sortBy, direction, page, size);
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

    public PortfolioViewResponse getPortfolioView(String userSub, Long portfolioId, Set<String> includes) {
        validateOwner(userSub, portfolioId);

        PortfolioSummaryResponse summary = includes.contains(INCLUDE_SUMMARY)
                ? summaryService.getSummary(portfolioId, null) : null;

        PagedResponse<PositionResponse> positions = includes.contains(INCLUDE_POSITIONS)
                ? summaryService.getPositionsPaged(portfolioId, null, null, null, null,
                        FIRST_PAGE, portfolioProperties.getView().getPositionPageSize()) : null;

        List<AllocationItem> allocation = includes.contains(INCLUDE_ALLOCATION)
                ? summaryService.getAllocation(portfolioId, ALLOCATION_MODE_ASSET_TYPE, null, null) : null;

        return new PortfolioViewResponse(summary, positions, allocation);
    }

    public Object getChart(String userSub, Long portfolioId, String type,
                           String range, String assetType, String assetCode) {
        validateOwner(userSub, portfolioId);
        if (CHART_TYPE_ASSET_SERIES.equals(type)) {
            return performanceService.getAssetSeries(portfolioId, assetType, assetCode, range);
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
