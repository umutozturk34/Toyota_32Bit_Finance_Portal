package com.finance.portfolio.service;
import com.finance.shared.service.AssetPricingPort;



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
        org.mockito.Mockito.lenient().when(assetSnapshotRepository.findLatestPerAsset(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void shouldCalculatePnlFromCurrentVsEntryPrice_whenBuildingAssetSnapshot() {
        PortfolioPosition pos = stubPosition(AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.50000000"), new BigDecimal("2500000.0000"));
        when(pricingPort.getExitPriceTry(MarketType.CRYPTO, "bitcoin"))
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
        when(pricingPort.getExitPriceTry(MarketType.STOCK, "DELISTED")).thenReturn(null);

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
        when(pricingPort.getExitPriceTry(MarketType.CRYPTO, "bitcoin"))
                .thenReturn(new BigDecimal("2500000.0000"));
        when(pricingPort.getExitPriceTry(MarketType.STOCK, "THYAO.IS"))
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
        when(pricingPort.getExitPriceTry(MarketType.FUND, "AAK"))
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

    private com.finance.market.viop.model.ViopContract derivativeContract(String symbol,
                                                                          BigDecimal contractSize,
                                                                          BigDecimal lastPrice) {
        return com.finance.market.viop.model.ViopContract.builder()
                .symbol(symbol)
                .kind(com.finance.market.viop.model.ViopContractKind.FUTURE)
                .contractSize(contractSize)
                .currency("TRY")
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    private com.finance.portfolio.derivative.model.DerivativePosition derivativePosition(
            com.finance.market.viop.model.ViopContract contract, BigDecimal entry, BigDecimal qty,
            com.finance.portfolio.derivative.model.DerivativeDirection direction) {
        return com.finance.portfolio.derivative.model.DerivativePosition.builder()
                .id(10L)
                .direction(direction)
                .entryDate(java.time.LocalDate.of(2026, 4, 1))
                .entryPrice(entry)
                .quantityLot(qty)
                .viopContract(contract)
                .build();
    }

    @Test
    void should_buildDerivativeSnapshotWithLivePrice_whenPositionIsOpen() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("2"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos,
                LocalDateTime.of(2026, 5, 1, 18, 0));

        assertThat(snap).isNotNull();
        assertThat(snap.getAssetType()).isEqualTo(AssetType.VIOP);
        assertThat(snap.getAssetCode()).isEqualTo("F_USDTRY0626");
        assertThat(snap.getUnitPriceTry()).isEqualByComparingTo("35.5000");
        assertThat(snap.getMarketValueTry()).isEqualByComparingTo("71000.0000");
        assertThat(snap.getTotalCostTry()).isEqualByComparingTo("70400.0000");
        assertThat(snap.getPnlTry()).isEqualByComparingTo("600.0000");
    }

    @Test
    void should_freezeAtClosePrice_whenPositionIsClosed() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("99.99"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);
        pos.closeWith(java.time.LocalDate.of(2026, 5, 1), new BigDecimal("36.00"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snap.getUnitPriceTry()).isEqualByComparingTo("36.0000");
        assertThat(snap.getPnlTry()).isEqualByComparingTo("800.0000");
    }

    @Test
    void should_invertPnl_whenShortPositionPriceGoesUp() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.SHORT);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snap.getPnlTry()).isEqualByComparingTo("-300.0000");
    }

    @Test
    void should_returnNullSnapshot_whenContractMissingOnDerivative() {
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                null, new BigDecimal("35.20"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshot(1L, pos, LocalDateTime.now());

        assertThat(snap).isNull();
    }

    @Test
    void should_useFxRateOverride_whenBuildingDerivativeSnapshotAt() {
        com.finance.market.viop.model.ViopContract c = derivativeContract(
                "F_X", new BigDecimal("100"), null);
        c.setCurrency("USD");
        com.finance.portfolio.derivative.model.DerivativePosition pos = derivativePosition(
                c, new BigDecimal("100.00"), new BigDecimal("1"),
                com.finance.portfolio.derivative.model.DerivativeDirection.LONG);

        PortfolioAssetDailySnapshot snap = service.buildDerivativeAssetSnapshotAt(1L, pos,
                LocalDateTime.now(), new BigDecimal("3.50"), new BigDecimal("32.00"));

        assertThat(snap.getUnitPriceTry()).isEqualByComparingTo("112.0000");
    }
}
