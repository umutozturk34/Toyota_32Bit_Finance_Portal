package com.finance.backend.service;

import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.*;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioFacade {

    private final PortfolioRepository portfolioRepository;
    private final OnboardingService onboardingService;
    private final PortfolioCrudService crudService;
    private final PortfolioTransactionService transactionService;
    private final PortfolioSummaryService summaryService;
    private final PortfolioPerformanceService performanceService;

    public void initialize(String userSub) {
        onboardingService.initialize(userSub);
    }

    public List<PortfolioResponse> listPortfolios(String userSub) {
        return crudService.listPortfolios(userSub);
    }

    public PortfolioResponse createPortfolio(String userSub, PortfolioCreateRequest request) {
        return crudService.createPortfolio(userSub, request);
    }

    public TransactionResponse executeTransaction(String userSub, Long portfolioId, TransactionRequest request) {
        validateOwner(userSub, portfolioId);
        return transactionService.execute(userSub, portfolioId, request);
    }

    public List<TransactionResponse> listTransactions(String userSub, Long portfolioId) {
        validateOwner(userSub, portfolioId);
        return crudService.listTransactions(portfolioId);
    }

    public PagedResponse<TransactionResponse> listTransactionsPaged(String userSub, Long portfolioId,
                                                                      String search, String assetType, String sortBy,
                                                                      String direction, int page, int size) {
        validateOwner(userSub, portfolioId);
        return crudService.listTransactionsPaged(portfolioId, search, assetType, sortBy, direction, page, size);
    }

    public List<PositionResponse> getPositions(String userSub, Long portfolioId) {
        validateOwner(userSub, portfolioId);
        return summaryService.getPositions(portfolioId);
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

    public List<AllocationItem> getAllocation(String userSub, Long portfolioId, String mode, String assetType) {
        validateOwner(userSub, portfolioId);
        return summaryService.getAllocation(portfolioId, mode, assetType);
    }

    public List<PerformancePoint> getPerformance(String userSub, Long portfolioId,
                                                   String range, String assetType) {
        validateOwner(userSub, portfolioId);
        return performanceService.getPerformance(portfolioId, range, assetType);
    }

    public List<AssetSeriesPoint> getAssetSeries(String userSub, Long portfolioId,
                                                   String assetType, String assetCode, String range) {
        validateOwner(userSub, portfolioId);
        return performanceService.getAssetSeries(portfolioId, assetType, assetCode, range);
    }

    public PortfolioViewResponse getPortfolioView(String userSub, Long portfolioId, Set<String> includes) {
        validateOwner(userSub, portfolioId);

        PortfolioSummaryResponse summary = includes.contains("summary")
                ? summaryService.getSummary(portfolioId, null) : null;

        PagedResponse<PositionResponse> positions = includes.contains("positions")
                ? summaryService.getPositionsPaged(portfolioId, null, null, null, null, 0, 10) : null;

        PagedResponse<TransactionResponse> transactionsPaged = includes.contains("transactions")
                ? crudService.listTransactionsPaged(portfolioId, null, null, null, null, 0, 5) : null;
        List<TransactionResponse> transactions = transactionsPaged != null ? transactionsPaged.content() : null;

        List<AllocationItem> allocation = includes.contains("allocation")
                ? summaryService.getAllocation(portfolioId, "assetType", null) : null;

        return new PortfolioViewResponse(summary, positions, transactions, allocation);
    }

    public Object getChart(String userSub, Long portfolioId, String type,
                           String range, String assetType, String assetCode) {
        validateOwner(userSub, portfolioId);
        if ("asset-series".equals(type)) {
            return performanceService.getAssetSeries(portfolioId, assetType, assetCode, range);
        }
        return performanceService.getPerformance(portfolioId, range, assetType);
    }

    private void validateOwner(String userSub, Long portfolioId) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }
}
