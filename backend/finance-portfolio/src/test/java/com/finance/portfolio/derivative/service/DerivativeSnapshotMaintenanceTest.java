package com.finance.portfolio.derivative.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.service.SnapshotCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerivativeSnapshotMaintenanceTest {

    private static final Long PORTFOLIO_ID = 7L;

    @Mock private ViopCandleRepository candleRepository;
    @Mock private HistoricalPricingPort historicalPricingPort;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private SnapshotCalculationService snapshotCalculator;
    @Mock private DerivativePositionRepository derivativePositionRepository;

    private DerivativeSnapshotMaintenance maintenance;

    @BeforeEach
    void setUp() {
        maintenance = new DerivativeSnapshotMaintenance(candleRepository, historicalPricingPort,
                assetSnapshotRepository, snapshotCalculator, derivativePositionRepository);
    }

    private ViopContract contract(String symbol, String currency) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .contractSize(BigDecimal.ONE)
                .currency(currency)
                .active(true)
                .build();
    }

    private DerivativePosition position(ViopContract c, LocalDate from, LocalDate to,
                                          BigDecimal closePrice, String entryPrice) {
        return DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal(entryPrice))
                .quantityLot(BigDecimal.ONE)
                .closeDate(to)
                .closePrice(closePrice)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
    }

    @Test
    void shouldReturn_whenContractIsNull() {
        DerivativePosition dp = DerivativePosition.builder()
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.now())
                .entryPrice(BigDecimal.ONE)
                .quantityLot(BigDecimal.ONE)
                .build();

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldReturn_whenEntryDateNull() {
        ViopContract c = contract("XU030F", "TRY");
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryPrice(BigDecimal.ONE)
                .quantityLot(BigDecimal.ONE)
                .build();

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldReturn_whenFromIsAfterTo() {
        ViopContract c = contract("XU030F", "TRY");
        DerivativePosition dp = position(c, LocalDate.of(2024, 6, 5), LocalDate.of(2024, 6, 1),
                new BigDecimal("100"), "100");

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldBuildSnapshotsAndSave_whenContractIsTryAndCandlesExist() {
        ViopContract c = contract("XU030F", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 3);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal("100"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("XU030F"), any(), any())).thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("100")).build(),
                        ViopCandle.builder().candleDate(from.plusDays(1).atStartOfDay()).close(new BigDecimal("110")).build()
                ));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(eq(PORTFOLIO_ID), eq(dp), any(), any(), any(), any()))
                .thenAnswer(inv -> PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    void shouldWriteValuelessCloseDayRow_keepingOnlyDailyMove() {
        ViopContract c = contract("F_XAUUSD0826", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate close = LocalDate.of(2024, 6, 2);
        DerivativePosition dp = position(c, from, close, new BigDecimal("110"), "100");
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(eq("F_XAUUSD0826"), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("100")).build()));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(eq(PORTFOLIO_ID), eq(dp), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    LocalDateTime ts = inv.getArgument(2);
                    return PortfolioAssetDailySnapshot.builder()
                            .assetCode("F_XAUUSD0826").snapshotDate(ts.toLocalDate()).createdAt(ts)
                            .quantity(BigDecimal.ONE).unitPriceTry(new BigDecimal("110"))
                            .marketValueTry(new BigDecimal("110")).totalCostTry(new BigDecimal("100"))
                            .pnlTry(new BigDecimal("10")).dailyPnlTry(new BigDecimal("50")).build();
                });

        maintenance.backfillSnapshots(dp);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotRepository).saveAll(captor.capture());
        // The close-day row carries ONLY the close-day move (dailyPnlTry 50); quantity/market/pnl are zeroed so
        // isCountableViopRow == false — it can't inflate the per-symbol rowMv on aggregate rebuild (the
        // partial-VIOP-sell "Tümü" TRY doubling). The pre-close (06-01) row stays countable (quantity 1).
        PortfolioAssetDailySnapshot closeDayRow = captor.getValue().stream()
                .filter(r -> close.equals(r.getSnapshotDate())
                        && r.getDailyPnlTry() != null && r.getDailyPnlTry().signum() != 0)
                .findFirst().orElse(null);
        assertThat(closeDayRow).isNotNull();
        assertThat(closeDayRow.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(closeDayRow.getMarketValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(closeDayRow.getDailyPnlTry()).isEqualByComparingTo("50");
        assertThat(captor.getValue()).anyMatch(r -> from.equals(r.getSnapshotDate())
                && r.getQuantity() != null && r.getQuantity().signum() != 0);
    }

    @Test
    void shouldSkipDateWhenCloseIsUnknown_whenNoCandleAndNoLastKnown() {
        ViopContract c = contract("XU030F", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldUseClosePriceOverride_whenAtCloseDate() {
        ViopContract c = contract("XU030F", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate close = LocalDate.of(2024, 6, 1);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .closeDate(close.plusDays(1))
                .closePrice(new BigDecimal("130"))
                .entryPrice(new BigDecimal("100"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("120")).build()
                ));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenReturn(PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository).saveAll(anyList());
    }

    @Test
    void shouldEmitZeroCloseRowWithZeroDailyPnl_notNegatedPriorValue_whenPositionClosesWithNoPeer() {
        // A closing position is not a daily MARKET loss — its proceeds settle to realized PnL — so the
        // synthetic post-close "value → 0" row must carry daily PnL 0, NOT −(prior market value). The old
        // negation surfaced as a phantom −100% daily K/Z, and polluted the "Tümü" aggregate of an emptied
        // portfolio (the stale row was still summed after the lot was gone).
        ViopContract c = contract("XU030F", "TRY");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate close = LocalDate.of(2024, 6, 2);
        DerivativePosition dp = position(c, from, close, new BigDecimal("130"), "100");
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("120")).build(),
                        ViopCandle.builder().candleDate(close.atStartOfDay()).close(new BigDecimal("130")).build()
                ));
        // Prior row carries a non-zero market value — the OLD code would have negated 5000 into the daily.
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenReturn(PortfolioAssetDailySnapshot.builder().marketValueTry(new BigDecimal("5000")).build());
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        maintenance.backfillSnapshots(dp);

        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotRepository).saveAll(captor.capture());
        // The SYNTHETIC post-close "value → 0" marker is emitted one second after the close-day row (which is now
        // itself value-less, also quantity 0) — target it by that exact timestamp so the assertion is unambiguous.
        PortfolioAssetDailySnapshot zeroRow = captor.getValue().stream()
                .filter(r -> close.atStartOfDay().plusSeconds(1).equals(r.getCreatedAt()))
                .findFirst().orElseThrow();
        assertThat(zeroRow.getMarketValueTry()).isEqualByComparingTo("0");
        assertThat(zeroRow.getDailyPnlTry()).isEqualByComparingTo("0");
    }

    @Test
    void shouldFetchFxSeries_whenContractIsForeign() {
        ViopContract c = contract("F_XAUUSD0625", "USD");
        LocalDate from = LocalDate.of(2024, 6, 1);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal("3000"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(ViopCandle.builder()
                        .candleDate(from.atStartOfDay()).close(new BigDecimal("10")).build()));
        when(historicalPricingPort.getPriceSeries(eq(MarketType.FOREX), eq("USD"), any(), any()))
                .thenReturn(Map.of(from, new BigDecimal("30")));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenReturn(PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        verify(historicalPricingPort).getPriceSeries(eq(MarketType.FOREX), eq("USD"), any(), any());
        verify(assetSnapshotRepository).saveAll(anyList());
    }

    @Test
    void shouldFallbackToLastFxRate_whenDailyFxIsMissing() {
        ViopContract c = contract("F_XAUUSD0625", "USD");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 2);
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c)
                .direction(DerivativeDirection.LONG)
                .entryDate(from)
                .entryPrice(new BigDecimal("3000"))
                .quantityLot(BigDecimal.ONE)
                .portfolio(Portfolio.builder().id(PORTFOLIO_ID).build())
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("10")).build(),
                        ViopCandle.builder().candleDate(to.atStartOfDay()).close(new BigDecimal("11")).build()
                ));
        when(historicalPricingPort.getPriceSeries(any(), any(), any(), any()))
                .thenReturn(Map.of(from, new BigDecimal("30")));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenReturn(PortfolioAssetDailySnapshot.builder().build());

        maintenance.backfillSnapshots(dp);

        verify(assetSnapshotRepository).saveAll(anyList());
    }

    @Test
    void shouldCarryForwardEntryNotional_whenEarlyDaysLackFxHistory() {
        // A USD-quoted VIOP whose FX history only starts AFTER its first held days must NOT skip those
        // early days (the old behaviour left no rows → the frontend splined a fake 0→entry ramp). They
        // are carried forward FLAT at the entry TRY notional. entryPrice (3000) is ALREADY TRY, so the
        // carry seed × fxRate=ONE × size(1) × lots(1) = 3000 — crucially NOT 30x (90000) inflated.
        ViopContract c = contract("F_XAUUSD0625", "USD");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate close = LocalDate.of(2024, 6, 3);
        DerivativePosition dp = position(c, from, close, new BigDecimal("3500"), "3000");
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("100")).build(),
                        ViopCandle.builder().candleDate(from.plusDays(1).atStartOfDay()).close(new BigDecimal("100")).build()
                ));
        // FX only exists on/after the close day, so the two early days find NO rate in the lookback.
        when(historicalPricingPort.getPriceSeries(eq(MarketType.FOREX), eq("USD"), any(), any()))
                .thenReturn(Map.of(close, new BigDecimal("30")));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        // Mirror the real assembler's market value: close × fxRate × contractSize(1) × lots(1).
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    BigDecimal exit = inv.getArgument(3);
                    BigDecimal fx = inv.getArgument(4);
                    return PortfolioAssetDailySnapshot.builder()
                            .quantity(BigDecimal.ONE)
                            .marketValueTry(exit.multiply(fx))
                            .build();
                });

        maintenance.backfillSnapshots(dp);

        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotRepository).saveAll(captor.capture());
        List<PortfolioAssetDailySnapshot> gapRows = captor.getValue().stream()
                .filter(r -> r.getQuantity() != null && r.getQuantity().signum() > 0)
                .filter(r -> r.getMarketValueTry() != null
                        && r.getMarketValueTry().compareTo(new BigDecimal("3000")) == 0)
                .toList();
        assertThat(gapRows).hasSize(2);
        assertThat(captor.getValue()).noneMatch(r -> r.getMarketValueTry() != null
                && r.getMarketValueTry().compareTo(new BigDecimal("90000")) == 0);
    }

    @Test
    void shouldValueEarlyCandleAtEarliestFx_whenEntryPredatesFxHistory() {
        // Entry predates all FX history (no prior rate), so the EARLIEST available rate (30) seeds valuation.
        // An early day whose candle moved (110 vs entry-native 100) is valued at candle × earliest FX
        // (110 × 30 = 3300) — the real price movement at the nearest known rate, not a flat entry-notional carry.
        ViopContract c = contract("F_XAUUSD0625", "USD");
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate close = LocalDate.of(2024, 6, 3);
        DerivativePosition dp = position(c, from, close, new BigDecimal("3500"), "3000");
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        ViopCandle.builder().candleDate(from.atStartOfDay()).close(new BigDecimal("100")).build(),
                        ViopCandle.builder().candleDate(from.plusDays(1).atStartOfDay()).close(new BigDecimal("110")).build()
                ));
        when(historicalPricingPort.getPriceSeries(eq(MarketType.FOREX), eq("USD"), any(), any()))
                .thenReturn(Map.of(close, new BigDecimal("30")));
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    BigDecimal exit = inv.getArgument(3);
                    BigDecimal fx = inv.getArgument(4);
                    return PortfolioAssetDailySnapshot.builder()
                            .quantity(BigDecimal.ONE)
                            .marketValueTry(exit.multiply(fx))
                            .build();
                });

        maintenance.backfillSnapshots(dp);

        ArgumentCaptor<List<PortfolioAssetDailySnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetSnapshotRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).anyMatch(r -> r.getMarketValueTry() != null
                && r.getMarketValueTry().compareTo(new BigDecimal("3300")) == 0);
    }

    @Test
    void priorOrEarliestRate_usesEarliest_whenTargetPredatesHistory() {
        Map<LocalDate, BigDecimal> series = Map.of(
                LocalDate.of(2024, 6, 3), new BigDecimal("30"),
                LocalDate.of(2024, 6, 5), new BigDecimal("32"));
        assertThat(DerivativeSnapshotMaintenance.priorOrEarliestRate(series, LocalDate.of(2024, 6, 1)))
                .isEqualByComparingTo("30");
        assertThat(DerivativeSnapshotMaintenance.earliestRate(series)).isEqualByComparingTo("30");
        assertThat(DerivativeSnapshotMaintenance.earliestRate(Map.of())).isNull();
    }

    @Test
    void shouldDoNothing_whenSymbolNull() {
        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, null);

        verify(assetSnapshotRepository, never()).findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any());
    }

    @Test
    void shouldDoNothing_whenNoSnapshotsFound() {
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of());

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        verify(assetSnapshotRepository, never()).saveAll(anyList());
        verify(assetSnapshotRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void shouldDoNothing_whenAllGroupsHaveSingleSnapshot() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot snap = PortfolioAssetDailySnapshot.builder()
                .id(1L)
                .createdAt(ts)
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(snap));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        verify(assetSnapshotRepository, never()).saveAll(anyList());
        verify(assetSnapshotRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void shouldMergeDuplicates_whenTwoSnapshotsShareCreatedAt() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot a = PortfolioAssetDailySnapshot.builder()
                .id(1L).createdAt(ts)
                .marketValueTry(new BigDecimal("100"))
                .pnlTry(new BigDecimal("10"))
                .quantity(new BigDecimal("2"))
                .totalCostTry(new BigDecimal("80"))
                .unitPriceTry(new BigDecimal("50"))
                .dailyPnlTry(new BigDecimal("5"))
                .build();
        PortfolioAssetDailySnapshot b = PortfolioAssetDailySnapshot.builder()
                .id(2L).createdAt(ts)
                .marketValueTry(new BigDecimal("200"))
                .pnlTry(new BigDecimal("20"))
                .quantity(new BigDecimal("4"))
                .totalCostTry(new BigDecimal("160"))
                .unitPriceTry(new BigDecimal("50"))
                .dailyPnlTry(new BigDecimal("10"))
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(a, b));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        verify(assetSnapshotRepository).saveAll(anyList());
        verify(assetSnapshotRepository).deleteAllByIdInBatch(anyList());
        assertThat(a.getMarketValueTry()).isEqualByComparingTo("300");
        assertThat(a.getQuantity()).isEqualByComparingTo("6");
        assertThat(a.getTotalCostTry()).isEqualByComparingTo("240");
        assertThat(a.getPnlTry()).isEqualByComparingTo("30");
        assertThat(a.getDailyPnlTry()).isEqualByComparingTo("15");
    }

    @Test
    void shouldKeepNullDaily_whenAllDuplicatesHaveNullDailyPnl() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot a = PortfolioAssetDailySnapshot.builder()
                .id(1L).createdAt(ts)
                .marketValueTry(new BigDecimal("100"))
                .quantity(new BigDecimal("2"))
                .totalCostTry(new BigDecimal("80"))
                .unitPriceTry(new BigDecimal("50"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        PortfolioAssetDailySnapshot b = PortfolioAssetDailySnapshot.builder()
                .id(2L).createdAt(ts)
                .marketValueTry(new BigDecimal("200"))
                .quantity(new BigDecimal("4"))
                .totalCostTry(new BigDecimal("160"))
                .unitPriceTry(new BigDecimal("50"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(a, b));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        assertThat(a.getDailyPnlTry()).isNull();
        assertThat(a.getDailyPnlPercent()).isNull();
    }

    @Test
    void shouldKeepKeeperUnitPrice_whenMergedQuantityIsZero() {
        LocalDateTime ts = LocalDateTime.of(2024, 6, 1, 12, 0);
        PortfolioAssetDailySnapshot a = PortfolioAssetDailySnapshot.builder()
                .id(1L).createdAt(ts)
                .marketValueTry(BigDecimal.ZERO)
                .quantity(BigDecimal.ZERO)
                .totalCostTry(BigDecimal.ZERO)
                .unitPriceTry(new BigDecimal("42"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        PortfolioAssetDailySnapshot b = PortfolioAssetDailySnapshot.builder()
                .id(2L).createdAt(ts)
                .marketValueTry(BigDecimal.ZERO)
                .quantity(BigDecimal.ZERO)
                .totalCostTry(BigDecimal.ZERO)
                .unitPriceTry(new BigDecimal("99"))
                .pnlTry(BigDecimal.ZERO)
                .build();
        when(assetSnapshotRepository.findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                any(), any(), any(), any(), any())).thenReturn(List.of(a, b));

        maintenance.consolidateSymbolSnapshots(PORTFOLIO_ID, "XU030F");

        assertThat(a.getUnitPriceTry()).isEqualByComparingTo("42");
    }

    @Test
    void shouldReturnNull_whenSeriesIsNull() {
        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(null, LocalDate.now());

        assertThat(rate).isNull();
    }

    @Test
    void shouldReturnNull_whenSeriesIsEmpty() {
        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(Map.of(), LocalDate.now());

        assertThat(rate).isNull();
    }

    @Test
    void shouldReturnExactDayRate_whenSeriesHasTarget() {
        LocalDate target = LocalDate.of(2024, 6, 1);
        Map<LocalDate, BigDecimal> series = Map.of(target, new BigDecimal("30"));

        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(series, target);

        assertThat(rate).isEqualByComparingTo("30");
    }

    @Test
    void shouldWalkBackUpTo30Days_whenPriorRateExists() {
        LocalDate target = LocalDate.of(2024, 6, 30);
        Map<LocalDate, BigDecimal> series = Map.of(target.minusDays(10), new BigDecimal("28"));

        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(series, target);

        assertThat(rate).isEqualByComparingTo("28");
    }

    @Test
    void shouldReturnNull_whenAllRatesOlderThan30Days() {
        LocalDate target = LocalDate.of(2024, 6, 30);
        Map<LocalDate, BigDecimal> series = Map.of(target.minusDays(60), new BigDecimal("28"));

        BigDecimal rate = DerivativeSnapshotMaintenance.closestPriorRate(series, target);

        assertThat(rate).isNull();
    }
}
