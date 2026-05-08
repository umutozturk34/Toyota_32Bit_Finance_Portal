package com.finance.portfolio.service;
import com.finance.common.service.AssetPricingPort;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.portfolio.model.AssetType;
import com.finance.common.model.MarketType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioDailySnapshot;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.service.support.CountingAssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotCalculationServiceTest {

    @Mock(answer = Answers.CALLS_REAL_METHODS) private AssetPricingPort pricingPort;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    private SnapshotCalculationService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotCalculationService(pricingPort, positionRepository,
                dailySnapshotRepository, assetSnapshotRepository, new PortfolioProperties());
    }

    @Test
    void shouldCalculatePnlFromCurrentVsEntryPrice_whenBuildingAssetSnapshot() {
        PortfolioPosition pos = stubPosition(AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.50000000"), new BigDecimal("2500000.0000"));
        when(pricingPort.getPriceTry(MarketType.CRYPTO, "bitcoin"))
                .thenReturn(new BigDecimal("2600000.0000"));
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 10, 23, 0);

        PortfolioAssetDailySnapshot snapshot = service.buildAssetSnapshot(1L, pos, timestamp);

        assertThat(snapshot.getMarketValueTry()).isEqualByComparingTo(new BigDecimal("1300000.0000"));
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("1250000.0000"));
        assertThat(snapshot.getPnlTry()).isEqualByComparingTo(new BigDecimal("50000.0000"));
        assertThat(snapshot.getUnitPriceTry()).isEqualByComparingTo(new BigDecimal("2600000.0000"));
        assertThat(snapshot.getSnapshotDate()).isEqualTo(timestamp.toLocalDate());
        assertThat(snapshot.getCreatedAt()).isEqualTo(timestamp);
    }

    @Test
    void shouldShowFullLossInAssetSnapshot_whenPriceIsNull() {
        PortfolioPosition pos = stubPosition(AssetType.STOCK, "DELISTED",
                new BigDecimal("100.00000000"), new BigDecimal("50.0000"));
        when(pricingPort.getPriceTry(MarketType.STOCK, "DELISTED")).thenReturn(null);

        PortfolioAssetDailySnapshot snapshot = service.buildAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snapshot.getUnitPriceTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getMarketValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getPnlTry()).isEqualByComparingTo(new BigDecimal("-5000.0000"));
    }

    @Test
    void shouldSumAllPositions_whenBuildingAggregateSnapshot() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1.00000000"), new BigDecimal("2400000.0000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100.00000000"), new BigDecimal("40.0000"))));
        when(pricingPort.getPriceTry(MarketType.CRYPTO, "bitcoin"))
                .thenReturn(new BigDecimal("2500000.0000"));
        when(pricingPort.getPriceTry(MarketType.STOCK, "THYAO.IS"))
                .thenReturn(new BigDecimal("50.0000"));

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(new BigDecimal("2505000.0000"));
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(new BigDecimal("2404000.0000"));
        assertThat(snapshot.getTotalPnlTry()).isEqualByComparingTo(new BigDecimal("101000.0000"));
    }

    @Test
    void shouldComputePnlPercentRelativeToEntryValue_whenBuildingAggregateSnapshot() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(stubPosition(AssetType.FUND, "AAK",
                        new BigDecimal("100.00000000"), new BigDecimal("100.0000"))));
        when(pricingPort.getPriceTry(MarketType.FUND, "AAK"))
                .thenReturn(new BigDecimal("110.0000"));

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo(new BigDecimal("10.0000"));
    }

    @Test
    void shouldIssueExactlyOneBatchPricingCall_whenBuildingAggregateSnapshot() {
        CountingAssetPricingPort counting = new CountingAssetPricingPort();
        counting.seedPrice("CRYPTO", "bitcoin", new BigDecimal("2500000.0000"));
        counting.seedPrice("STOCK", "THYAO.IS", new BigDecimal("50.0000"));
        counting.seedPrice("FUND", "AAK", new BigDecimal("110.0000"));

        SnapshotCalculationService countedService = new SnapshotCalculationService(counting, positionRepository,
                dailySnapshotRepository, assetSnapshotRepository, new PortfolioProperties());

        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(
                        stubPosition(AssetType.CRYPTO, "bitcoin", new BigDecimal("1.00000000"), new BigDecimal("2400000.0000")),
                        stubPosition(AssetType.STOCK, "THYAO.IS", new BigDecimal("100.00000000"), new BigDecimal("40.0000")),
                        stubPosition(AssetType.FUND, "AAK", new BigDecimal("50.00000000"), new BigDecimal("100.0000"))));

        countedService.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(counting.batchPricesCalls()).isEqualTo(1);
        assertThat(counting.priceCalls()).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroAggregate_whenNoPositionsExist() {
        Portfolio portfolio = Portfolio.builder().id(1L).build();
        when(positionRepository.findByPortfolioId(1L)).thenReturn(List.of());

        PortfolioDailySnapshot snapshot = service.buildAggregateSnapshot(portfolio, LocalDateTime.now());

        assertThat(snapshot.getPnlPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getTotalValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.getTotalCostTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private PortfolioPosition stubPosition(AssetType type, String code, BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .assetType(type)
                .assetCode(code)
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(LocalDateTime.now())
                .build();
    }
}
