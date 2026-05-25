package com.finance.portfolio.service;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.shared.service.PortfolioSnapshotPort;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import org.springframework.beans.factory.ObjectProvider;

import com.finance.portfolio.repository.PortfolioRepository;

import com.finance.portfolio.repository.PortfolioPositionRepository;

import com.finance.portfolio.model.PortfolioPosition;

import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;

import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.Portfolio;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;

import com.finance.portfolio.model.AssetType;


import com.finance.shared.util.BatchLogHelper;
import com.finance.shared.util.BatchUpdateRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
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
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<EventPublisherPort> events;

    @Override
    public void onMarketUpdate(MarketType marketType) {
        AssetType type = AssetType.fromMarketType(marketType);
        if (type == null) {
            return;
        }
        List<Portfolio> portfolios = portfolioRepository.findAll();
        LocalDateTime batchTimestamp = LocalDateTime.now();

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                portfolios,
                portfolio -> transactionTemplate.executeWithoutResult(status -> {
                    if (type == AssetType.VIOP) {
                        snapshotDerivativePositions(portfolio, batchTimestamp);
                    } else {
                        boolean hasPositions = !positionRepository
                                .findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                                        portfolio.getId(), TrackedAssetType.valueOf(type.name()), BigDecimal.ZERO)
                                .isEmpty();
                        if (hasPositions) {
                            insertAssetSnapshots(portfolio, type, batchTimestamp);
                        }
                    }
                    insertAggregateSnapshot(portfolio, batchTimestamp);
                }),
                p -> String.valueOf(p.getId()),
                "portfolio-" + type + "-snapshot",
                1,
                (p, e) -> log.error("Portfolio {} snapshot failed for portfolioId={}: {}",
                        type, p.getId(), e.getMessage(), e),
                null, null
        );

        BatchLogHelper.logSummary(log, type + " portfolio snapshot", result);
    }

    private void snapshotDerivativePositions(Portfolio portfolio, LocalDateTime batchTimestamp) {
        List<DerivativePosition> positions = derivativePositionRepository
                .findByPortfolioId(portfolio.getId());
        if (positions.isEmpty()) {
            return;
        }
        for (DerivativePosition position : positions) {
            PortfolioAssetDailySnapshot snapshot = calculator.buildDerivativeAssetSnapshot(
                    portfolio.getId(), position, batchTimestamp);
            if (snapshot != null) {
                assetSnapshotRepository.save(snapshot);
            }
        }
    }

    public void generateDailySnapshots(String source) {
        LocalDate today = LocalDate.now();
        List<Portfolio> portfolios = portfolioRepository.findAll();

        BatchUpdateRunner.Result result = BatchUpdateRunner.run(
                portfolios,
                portfolio -> transactionTemplate.executeWithoutResult(status -> {
                    generateFullSnapshot(portfolio);
                }),
                p -> String.valueOf(p.getId()),
                "daily-snapshot",
                1,
                (p, e) -> log.error("Daily portfolio snapshot failed for portfolioId={} (source={}): {}",
                        p.getId(), source, e.getMessage(), e),
                null, null
        );

        if (!portfolios.isEmpty()) {
            events.ifAvailable(port -> port.publish(PortfolioUpdatedEvent.of(source)));
        }

        BatchLogHelper.logSummary(log, source + " portfolio snapshot", result);
    }

    private void insertAssetSnapshots(Portfolio portfolio, AssetType assetType,
                                       LocalDateTime batchTimestamp) {
        Long pid = portfolio.getId();
        List<PortfolioPosition> open = positionRepository
                .findByPortfolioIdAndTrackedAsset_AssetTypeAndQuantityGreaterThan(
                        pid, TrackedAssetType.valueOf(assetType.name()), BigDecimal.ZERO)
                .stream().filter(p -> !p.isClosed()).toList();
        calculator.buildAssetSnapshotsForPositions(pid, open, batchTimestamp)
                .forEach(assetSnapshotRepository::save);
    }

    private PortfolioDailySnapshot insertAggregateSnapshot(Portfolio portfolio, LocalDateTime batchTimestamp) {
        dailySnapshotRepository.deleteByPortfolioIdAndSnapshotDate(portfolio.getId(), batchTimestamp.toLocalDate());
        return dailySnapshotRepository.save(calculator.buildAggregateSnapshot(portfolio, batchTimestamp));
    }

    private PortfolioDailySnapshot generateFullSnapshot(Portfolio portfolio) {
        Long pid = portfolio.getId();
        LocalDateTime batchTimestamp = LocalDateTime.now();
        List<PortfolioPosition> open = positionRepository
                .findByPortfolioIdAndQuantityGreaterThan(pid, BigDecimal.ZERO)
                .stream().filter(p -> !p.isClosed()).toList();
        List<DerivativePosition> derivatives = derivativePositionRepository.findByPortfolioId(pid);

        if (open.isEmpty() && derivatives.isEmpty()) return null;

        calculator.buildAssetSnapshotsForPositions(pid, open, batchTimestamp)
                .forEach(assetSnapshotRepository::save);
        for (DerivativePosition dpos : derivatives) {
            PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshot(pid, dpos, batchTimestamp);
            if (snap != null) assetSnapshotRepository.save(snap);
        }

        return insertAggregateSnapshot(portfolio, batchTimestamp);
    }
}
