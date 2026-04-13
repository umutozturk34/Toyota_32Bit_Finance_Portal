package com.finance.backend.service;

import com.finance.backend.model.*;
import com.finance.backend.repository.*;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService implements PortfolioSnapshotPort {

    private final SnapshotCalculationService calculator;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void onMarketUpdate(MarketType marketType) {
        AssetType type = AssetType.valueOf(marketType.name());
        List<Portfolio> portfolios = portfolioRepository.findAll();
        LocalDateTime batchTimestamp = LocalDateTime.now();

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                portfolios,
                portfolio -> transactionTemplate.executeWithoutResult(status -> {
                    boolean hasPositions = !positionRepository
                            .findByPortfolioIdAndAssetTypeAndQuantityGreaterThan(
                                    portfolio.getId(), type, BigDecimal.ZERO)
                            .isEmpty();
                    if (hasPositions) {
                        insertAssetSnapshots(portfolio, type, batchTimestamp);
                        insertAggregateSnapshot(portfolio, batchTimestamp);
                    }
                }),
                p -> String.valueOf(p.getId()),
                "portfolio-" + type + "-snapshot",
                1, null, null, null
        );

        BatchLogHelper.logSummary(log, type + " portfolio snapshot", result);
    }

    public void generateDailySnapshots() {
        LocalDate today = LocalDate.now();
        List<Portfolio> portfolios = portfolioRepository.findAll();

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                portfolios,
                portfolio -> transactionTemplate.executeWithoutResult(status -> {
                    if (dailySnapshotRepository.existsByPortfolioIdAndSnapshotDate(portfolio.getId(), today)) {
                        return;
                    }
                    generateFullSnapshot(portfolio);
                }),
                p -> String.valueOf(p.getId()),
                "daily-snapshot",
                1, null, null, null
        );

        BatchLogHelper.logSummary(log, "Daily portfolio snapshot (fallback)", result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void snapshotInNewTransaction(Long portfolioId, AssetType assetType, String assetCode) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElse(null);
        if (portfolio == null) {
            log.warn("Portfolio not found for snapshot: {}", portfolioId);
            return;
        }
        LocalDateTime batchTimestamp = LocalDateTime.now();
        insertAssetSnapshots(portfolio, assetType, batchTimestamp);
        insertClosingSnapshotIfSold(portfolioId, assetType, assetCode, batchTimestamp);
        insertAggregateSnapshot(portfolio, batchTimestamp);
    }

    private void insertClosingSnapshotIfSold(Long portfolioId, AssetType assetType,
                                               String assetCode, LocalDateTime batchTimestamp) {
        if (assetCode == null) return;
        positionRepository.findByPortfolioIdAndAssetTypeAndAssetCode(portfolioId, assetType, assetCode)
                .filter(pos -> pos.getQuantity().compareTo(BigDecimal.ZERO) == 0)
                .ifPresent(pos -> assetSnapshotRepository.save(
                        calculator.buildAssetSnapshot(portfolioId, pos, batchTimestamp)));
    }

    private void insertAssetSnapshots(Portfolio portfolio, AssetType assetType,
                                       LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndAssetTypeAndQuantityGreaterThan(pid, assetType, BigDecimal.ZERO);

        for (PortfolioPosition pos : positions) {
            assetSnapshotRepository.save(calculator.buildAssetSnapshot(pid, pos, batchTimestamp));
        }
    }

    private void insertAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        dailySnapshotRepository.save(calculator.buildAggregateSnapshot(portfolio, batchTimestamp));
    }

    private void generateFullSnapshot(Portfolio portfolio) {
        Long pid = portfolio.getId();
        LocalDateTime batchTimestamp = LocalDateTime.now();
        List<PortfolioPosition> positions = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(pid, BigDecimal.ZERO);

        for (PortfolioPosition pos : positions) {
            assetSnapshotRepository.save(calculator.buildAssetSnapshot(pid, pos, batchTimestamp));
        }

        insertAggregateSnapshot(portfolio, batchTimestamp);
    }
}
