package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerivativeSnapshotAssemblerTest {

    private static final Long PORTFOLIO_ID = 5L;
    private static final LocalDateTime BATCH_TS = LocalDateTime.of(2024, 6, 1, 12, 0);

    @Mock private AssetPricingPort pricingPort;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private com.finance.market.core.service.HistoricalPricingPort historicalPricingPort;

    private DerivativeSnapshotAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new DerivativeSnapshotAssembler(pricingPort, assetSnapshotRepository, historicalPricingPort);
    }

    private ViopContract contract(String symbol, String size, String currency) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .contractSize(size != null ? new BigDecimal(size) : null)
                .currency(currency)
                .active(true)
                .build();
    }

    private DerivativePosition position(ViopContract c, DerivativeDirection dir, String entry, String qty) {
        return DerivativePosition.builder()
                .viopContract(c)
                .direction(dir)
                .entryDate(LocalDate.of(2024, 1, 1))
                .entryPrice(new BigDecimal(entry))
                .quantityLot(new BigDecimal(qty))
                .build();
    }

    @Test
    void shouldReturnNull_whenContractIsNull() {
        DerivativePosition dp = DerivativePosition.builder()
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.now())
                .entryPrice(BigDecimal.ONE)
                .quantityLot(BigDecimal.ONE)
                .build();

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("100"), null, null);

        assertThat(result).isNull();
    }

    @Test
    void shouldBuildSnapshot_whenLongFutureWithPriorSnapshot() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "100", "2");
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .unitPriceTry(new BigDecimal("110"))
                .marketValueTry(new BigDecimal("2200"))
                .build();
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(PORTFOLIO_ID), eq(AssetType.VIOP), eq("XU030F"), eq(BATCH_TS)))
                .thenReturn(Optional.of(prior));

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("120"), null, null);

        assertThat(result).isNotNull();
        assertThat(result.getPortfolioId()).isEqualTo(PORTFOLIO_ID);
        assertThat(result.getAssetType()).isEqualTo(AssetType.VIOP);
        assertThat(result.getAssetCode()).isEqualTo("XU030F");
        assertThat(result.getQuantity()).isEqualByComparingTo("2");
        assertThat(result.getUnitPriceTry()).isEqualByComparingTo("120");
        assertThat(result.getMarketValueTry()).isEqualByComparingTo("2400");
        assertThat(result.getTotalCostTry()).isEqualByComparingTo("2000");
        assertThat(result.getPnlTry()).isEqualByComparingTo("400");
        assertThat(result.getDailyPnlTry()).isEqualByComparingTo("200");
        assertThat(result.getDailyPnlPercent()).isNotNull();
    }

    @Test
    void shouldComputeCloseDayDailyMove_whenPositionClosedToday() {
        // A leg closed TODAY still moved today (prior close -> close price); that move must count in Günlük K/Z
        // exactly as an open position's would, so a hedge's closed-today leg nets with its still-open peer.
        // (The obsolete close-day EMPTY skip dropped this move, leaving the open leg's move un-netted.)
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition closedToday = DerivativePosition.builder()
                .viopContract(c).direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2024, 1, 1))
                .entryPrice(new BigDecimal("100")).quantityLot(new BigDecimal("2"))
                .closeDate(BATCH_TS.toLocalDate())
                .closePrice(new BigDecimal("120"))
                .build();
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .unitPriceTry(new BigDecimal("110"))
                .marketValueTry(new BigDecimal("2200"))
                .build();
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                eq(PORTFOLIO_ID), eq(AssetType.VIOP), eq("XU030F"), eq(BATCH_TS)))
                .thenReturn(Optional.of(prior));

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, closedToday, BATCH_TS, new BigDecimal("120"), null, null);

        // (120 - 110) * size 10 * qty 2 = 200 — the close-day move, NOT null
        assertThat(result.getDailyPnlTry()).isEqualByComparingTo("200");
        assertThat(result.getDailyPnlPercent()).isNotNull();
    }

    @Test
    void shouldReturnEmptyDailyDelta_whenNoPriorSnapshot() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "100", "2");
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("120"), null, null);

        assertThat(result.getDailyPnlTry()).isNull();
        assertThat(result.getDailyPnlPercent()).isNull();
    }

    @Test
    void shouldUsePriorOverride_whenSpecifiedSoNoDbCall() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "100", "2");
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .unitPriceTry(new BigDecimal("110"))
                .marketValueTry(new BigDecimal("2200"))
                .build();

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("120"), null, prior);

        assertThat(result.getDailyPnlTry()).isEqualByComparingTo("200");
    }

    @Test
    void shouldComputeShortPnl_whenDirectionIsShort() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.SHORT, "100", "2");
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("80"), null, null);

        // A SHORT profits when price drops (100→80): pnl = (100−80)×10×2 = 400, stored DIRECTION-AWARE.
        assertThat(result.getPnlTry()).isEqualByComparingTo("400");
        // Market value = current notional (80×10×2 = 1600), the mark-to-market value. For a SHORT the
        // notional falls as the position profits, so value − cost (1600 − 2000 = −400) ≠ pnl (+400) —
        // consumers read pnlTry, not (value − cost).
        assertThat(result.getTotalCostTry()).isEqualByComparingTo("2000");
        assertThat(result.getMarketValueTry()).isEqualByComparingTo("1600");
    }

    @Test
    void shouldApplyFxRateOverride_whenProvided() {
        ViopContract c = contract("F_XAUUSD0625", "1", "USD");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "10", "1");
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("10"), new BigDecimal("30"), null);

        assertThat(result.getUnitPriceTry()).isEqualByComparingTo("300");
    }

    @Test
    void shouldFallbackToContractFxRate_whenFxOverrideMissing() {
        ViopContract c = contract("F_XAUUSD0625", "1", "USD");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "10", "1");
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(pricingPort.getPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(new BigDecimal("25"));

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("10"), null, null);

        assertThat(result.getUnitPriceTry()).isEqualByComparingTo("250");
    }

    @Test
    void shouldUseOneFx_whenCurrencyIsTRY() {
        ViopContract c = contract("XU030F", "1", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "10", "1");
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("15"), null, null);

        assertThat(result.getUnitPriceTry()).isEqualByComparingTo("15");
    }

    @Test
    void shouldReturnNullSnapshot_whenForeignContractAndForexRateMissing() {
        ViopContract c = contract("F_XAUUSD0625", "1", "USD");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "10", "1");
        lenient().when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(pricingPort.getPriceTry(eq(MarketType.FOREX), eq("USD"))).thenReturn(null);

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("10"), null, null);

        // Foreign contract + no FX → null. Earlier "fallback to 1" silently persisted native USD
        // as TRY, a ~30x corruption on USD-denominated futures during scraper outages.
        assertThat(result).isNull();
    }

    @Test
    void shouldUseDefaultContractSize_whenContractSizeNull() {
        ViopContract c = contract("XU030F", null, "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "100", "2");
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("110"), null, null);

        assertThat(result.getMarketValueTry()).isEqualByComparingTo("220");
    }

    @Test
    void shouldUseZeroQty_whenQuantityLotIsNull() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = DerivativePosition.builder()
                .viopContract(c).direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.now())
                .entryPrice(new BigDecimal("100"))
                .build();
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("110"), null, null);

        assertThat(result.getQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void shouldUseZeroExitPrice_whenExitPriceNull() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "100", "2");
        when(assetSnapshotRepository.findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, null, null, null);

        assertThat(result.getUnitPriceTry()).isEqualByComparingTo("0");
        assertThat(result.getMarketValueTry()).isEqualByComparingTo("0");
    }

    @Test
    void shouldReturnNullDailyPercent_whenPriorMarketValueZero() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "100", "2");
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder()
                .unitPriceTry(new BigDecimal("100"))
                .marketValueTry(BigDecimal.ZERO)
                .build();

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("110"), null, prior);

        assertThat(result.getDailyPnlTry()).isNotNull();
        assertThat(result.getDailyPnlPercent()).isNull();
    }

    @Test
    void shouldReturnEmptyDailyDelta_whenPriorUnitPriceNull() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition dp = position(c, DerivativeDirection.LONG, "100", "2");
        PortfolioAssetDailySnapshot prior = PortfolioAssetDailySnapshot.builder().build();

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, dp, BATCH_TS, new BigDecimal("110"), null, prior);

        assertThat(result.getDailyPnlTry()).isNull();
        assertThat(result.getDailyPnlPercent()).isNull();
    }

    @Test
    void shouldAnchorDailyToEntryPrice_whenOpenedOnSnapshotDay() {
        ViopContract c = contract("XU030F", "10", "TRY");
        DerivativePosition openedToday = DerivativePosition.builder()
                .viopContract(c).direction(DerivativeDirection.LONG)
                .entryDate(BATCH_TS.toLocalDate())   // opened ON the snapshot day → no prior to measure against
                .entryPrice(new BigDecimal("100"))
                .quantityLot(new BigDecimal("2"))
                .build();

        PortfolioAssetDailySnapshot result = assembler.buildAt(
                PORTFOLIO_ID, openedToday, BATCH_TS, new BigDecimal("110"), null, null);

        // Daily K/Z = gain since entry = (110 − 100) × size(10) × lots(2) = 200; NOT 0 and NOT the full notional
        // (2200) booked against a phantom zero prior. Percent = 200 / cost(100×10×2 = 2000) = 10%.
        assertThat(result.getDailyPnlTry()).isEqualByComparingTo("200");
        assertThat(result.getDailyPnlPercent()).isEqualByComparingTo("10");
    }
}
