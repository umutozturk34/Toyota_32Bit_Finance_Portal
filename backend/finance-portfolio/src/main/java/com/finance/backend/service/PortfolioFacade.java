package com.finance.backend.service;

import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.request.TransactionRequest;
import com.finance.backend.dto.response.*;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<PositionResponse> getPositions(String userSub, Long portfolioId) {
        validateOwner(userSub, portfolioId);
        return summaryService.getPositions(portfolioId);
    }

    public PortfolioSummaryResponse getSummary(String userSub, Long portfolioId, String assetType) {
        validateOwner(userSub, portfolioId);
        return summaryService.getSummary(portfolioId, assetType);
    }

    public List<AllocationItem> getAllocation(String userSub, Long portfolioId, String mode) {
        validateOwner(userSub, portfolioId);
        return summaryService.getAllocation(portfolioId, mode);
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

    private void validateOwner(String userSub, Long portfolioId) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));
    }
}
