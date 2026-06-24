package com.finance.portfolio.service.summary;

import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.response.AssetAggregateResponse;
import com.finance.portfolio.dto.response.CurrencyFramePct;
import com.finance.portfolio.service.pricing.DerivativePricingResolver;
import com.finance.portfolio.service.pricing.MultiCurrencyPnlCalculator;
import com.finance.portfolio.service.pricing.RealReturnCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for {@link ViopAssetAggregateService}: the direction-aware per-symbol VIOP aggregate
 * (empty/open/closed/hedge/direction-filter/raw-notional-frame) and the lenient direction parser. All four
 * collaborators are mocked so the assertions isolate this service's arithmetic and branching, never the
 * frame/FX maths that lives in {@link MultiCurrencyPnlCalculator}.
 */
@ExtendWith(MockitoExtension.class)
class ViopAssetAggregateServiceTest {

    private static final long PORTFOLIO_ID = 1L;
    private static final String SYMBOL = "F_HEDGETRY0626";

    @Mock private DerivativePositionRepository derivativePositionRepository;
    @Mock private MultiCurrencyPnlCalculator multiCurrencyPnlCalculator;
    @Mock private DerivativePricingResolver derivativePricingResolver;
    @Mock private SummaryEntryFootprintBuilder summaryFootprintBuilder;

    @InjectMocks private ViopAssetAggregateService service;

    private static final Map<String, CurrencyFramePct> STUB_FRAMES =
            Map.of("TRY", new CurrencyFramePct(null, null, null, null, null, null));

    // --- parseDirectionOrNull ---------------------------------------------------------------------

    @Test
    void should_parseLong_when_directionIsLowercaseWithWhitespace() {
        DerivativeDirection result = service.parseDirectionOrNull("  long  ");

        assertThat(result).isEqualTo(DerivativeDirection.LONG);
    }

    @ParameterizedTest
    @CsvSource({
            "LONG, LONG",
            "long, LONG",
            "Long, LONG",
            "SHORT, SHORT",
            "short, SHORT",
            "ShOrT, SHORT",
    })
    void should_parseDirectionCaseInsensitively_when_valueIsLongOrShort(String input, DerivativeDirection expected) {
        DerivativeDirection result = service.parseDirectionOrNull(input);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "FLAT", "buy", "123", "long short"})
    void should_returnNullFilter_when_directionIsNullBlankOrGarbage(String input) {
        DerivativeDirection result = service.parseDirectionOrNull(input);

        assertThat(result).isNull();
    }

    // --- viopAssetAggregate: empty ----------------------------------------------------------------

    @Test
    void should_returnZeroAggregate_when_noLotsMatchSymbol() {
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID))
                .thenReturn(List.of(openLot(DerivativeDirection.LONG, "100", "1", "OTHERSYM0626", "120")));

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        assertThat(result.assetType()).isEqualTo("VIOP");
        assertThat(result.assetCode()).isEqualTo(SYMBOL);
        assertThat(result.lotCount()).isZero();
        assertThat(result.totalQuantity()).isEqualByComparingTo("0");
        assertThat(result.totalMarketValueTry()).isEqualByComparingTo("0");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("0");
        assertThat(result.frames()).isEmpty();
        verify(multiCurrencyPnlCalculator, never()).computeFromFootprints(any(), any(), any(), any(), any());
    }

    @Test
    void should_returnZeroAggregate_when_repositoryHasNoPositions() {
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        assertThat(result.lotCount()).isZero();
        assertThat(result.weightedAvgEntryPrice()).isEqualByComparingTo("0");
        assertThat(result.currentPriceTry()).isEqualByComparingTo("0");
        assertThat(result.earliestEntryDate()).isNull();
    }

    // --- viopAssetAggregate: single open LONG -----------------------------------------------------

    @Test
    void should_computeDirectionAwarePnlAndDisplayFields_when_singleOpenLong() {
        DerivativePosition longLot = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(longLot));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY"))
                .thenReturn(new BigDecimal("120"));
        when(summaryFootprintBuilder.openDerivativeNotionalTry(longLot)).thenReturn(new BigDecimal("120"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        // LONG 100 -> 120, size 1, lot 1 => +20 direction-aware PnL; entry basis 100 => +20%.
        assertThat(result.lotCount()).isEqualTo(1);
        assertThat(result.totalQuantity()).isEqualByComparingTo("1");
        assertThat(result.weightedAvgEntryPrice()).isEqualByComparingTo("100.0000");
        assertThat(result.currentPriceTry()).isEqualByComparingTo("120");
        assertThat(result.totalEntryValueTry()).isEqualByComparingTo("100.0000");
        assertThat(result.totalMarketValueTry()).isEqualByComparingTo("120.0000");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("20.0000");
        assertThat(result.pnlPercent()).isEqualByComparingTo("20.0000");
        assertThat(result.frames()).isSameAs(STUB_FRAMES);
        assertThat(result.earliestEntryDate()).isEqualTo(LocalDateTime.of(2026, 4, 1, 12, 0));
    }

    // --- viopAssetAggregate: hedge nets to zero ---------------------------------------------------

    @Test
    void should_netHedgePnlToZero_when_openLongAndShortBalance() {
        DerivativePosition longLeg = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        DerivativePosition shortLeg = openLot(DerivativeDirection.SHORT, "100", "1", SYMBOL, "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(longLeg, shortLeg));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY"))
                .thenReturn(new BigDecimal("120"));
        when(summaryFootprintBuilder.openDerivativeNotionalTry(any())).thenReturn(new BigDecimal("120"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        // +20 LONG and -20 SHORT net to exactly 0; gross entry basis is the summed 200 -> 0%.
        assertThat(result.lotCount()).isEqualTo(2);
        assertThat(result.totalEntryValueTry()).isEqualByComparingTo("200.0000");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("0.0000");
        assertThat(result.pnlPercent()).isEqualByComparingTo("0.0000");
    }

    // --- viopAssetAggregate: direction filter -----------------------------------------------------

    @Test
    void should_scopeAggregateToLongLeg_when_directionFilterIsLong() {
        DerivativePosition longLeg = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        DerivativePosition shortLeg = openLot(DerivativeDirection.SHORT, "100", "1", SYMBOL, "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(longLeg, shortLeg));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY"))
                .thenReturn(new BigDecimal("120"));
        when(summaryFootprintBuilder.openDerivativeNotionalTry(longLeg)).thenReturn(new BigDecimal("120"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, DerivativeDirection.LONG);

        // Only the LONG leg survives the filter: +20 PnL on a 100 basis.
        assertThat(result.lotCount()).isEqualTo(1);
        assertThat(result.totalEntryValueTry()).isEqualByComparingTo("100.0000");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("20.0000");
        verify(summaryFootprintBuilder, never()).openDerivativeNotionalTry(shortLeg);
    }

    @Test
    void should_scopeAggregateToShortLeg_when_directionFilterIsShort() {
        DerivativePosition longLeg = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        DerivativePosition shortLeg = openLot(DerivativeDirection.SHORT, "100", "1", SYMBOL, "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(longLeg, shortLeg));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY"))
                .thenReturn(new BigDecimal("120"));
        when(summaryFootprintBuilder.openDerivativeNotionalTry(shortLeg)).thenReturn(new BigDecimal("120"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, DerivativeDirection.SHORT);

        // SHORT 100 -> 120 loses 20; the LONG leg is excluded entirely.
        assertThat(result.lotCount()).isEqualTo(1);
        assertThat(result.totalPnlTry()).isEqualByComparingTo("-20.0000");
        assertThat(result.pnlPercent()).isEqualByComparingTo("-20.0000");
    }

    // --- viopAssetAggregate: closed lot + frame raw notional ---------------------------------------

    @Test
    void should_foldClosedRealizedPnlAndFeedRawNotionalFrame_when_mixOfOpenAndClosedLots() {
        DerivativePosition openLong = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        DerivativePosition closedLong = closedLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "130");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID))
                .thenReturn(List.of(openLong, closedLong));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY"))
                .thenReturn(new BigDecimal("120"));
        when(summaryFootprintBuilder.openDerivativeNotionalTry(openLong)).thenReturn(new BigDecimal("121"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);
        ArgumentCaptor<BigDecimal> frameValueCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<List<RealReturnCalculator.EntryFootprint>> footprintsCaptor = footprintCaptor();

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        // Open LONG +20, closed LONG (100 -> 130) +30 => total direction-aware PnL +50 over basis 200 (=25%).
        assertThat(result.lotCount()).isEqualTo(1);                       // only the OPEN lot counts as a "lot"
        assertThat(result.totalQuantity()).isEqualByComparingTo("1");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("50.0000");
        assertThat(result.pnlPercent()).isEqualByComparingTo("25.0000");
        assertThat(result.totalEntryValueTry()).isEqualByComparingTo("100.0000"); // open entry value only
        assertThat(result.totalMarketValueTry()).isEqualByComparingTo("120.0000"); // open equity entry+pnl
        // frameValueTry = open notional (121, from builder) + closed proceeds (entry 100 + realized 30 = 130) = 251.
        verify(multiCurrencyPnlCalculator).computeFromFootprints(
                footprintsCaptor.capture(), frameValueCaptor.capture(), any(), any(), any());
        assertThat(frameValueCaptor.getValue()).isEqualByComparingTo("251.0000");
        // One closed footprint emitted by the service itself (the mocked builder adds nothing for open legs).
        assertThat(footprintsCaptor.getValue()).hasSize(1);
        RealReturnCalculator.EntryFootprint closedFp = footprintsCaptor.getValue().get(0);
        assertThat(closedFp.exitDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(closedFp.entryValueTry()).isEqualByComparingTo("100");
        assertThat(closedFp.exitValueTry()).isEqualByComparingTo("130");
        assertThat(closedFp.directionSign()).isEqualTo(1);
    }

    @Test
    void should_flipClosedFootprintSign_when_closedLotIsShort() {
        DerivativePosition closedShort = closedLot(DerivativeDirection.SHORT, "100", "1", SYMBOL, "130");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closedShort));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);
        ArgumentCaptor<List<RealReturnCalculator.EntryFootprint>> footprintsCaptor = footprintCaptor();

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        // SHORT 100 -> 130 loses 30; all lots closed => no open lot, currentPrice/weightedAvg fall to 0.
        assertThat(result.lotCount()).isZero();
        assertThat(result.totalQuantity()).isEqualByComparingTo("0");
        assertThat(result.currentPriceTry()).isEqualByComparingTo("0");
        assertThat(result.weightedAvgEntryPrice()).isEqualByComparingTo("0");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("-30.0000");
        verify(multiCurrencyPnlCalculator).computeFromFootprints(footprintsCaptor.capture(), any(), any(), any(), any());
        RealReturnCalculator.EntryFootprint fp = footprintsCaptor.getValue().get(0);
        assertThat(fp.directionSign()).isEqualTo(-1);
        // SHORT closed proceeds = entry notional 100 + realized (-30) = 70.
        assertThat(fp.exitValueTry()).isEqualByComparingTo("70");
        verify(summaryFootprintBuilder, never()).openDerivativeNotionalTry(any());
    }

    // --- viopAssetAggregate: degraded / null-input branches ---------------------------------------

    @Test
    void should_skipLotWithNullNotional_when_entryPriceMissing() {
        DerivativePosition broken = DerivativePosition.builder()
                .id(99L).direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(null)            // nominalExposure() -> null => the lot is skipped
                .quantityLot(BigDecimal.ONE)
                .viopContract(tryContract(SYMBOL, "120"))
                .build();
        DerivativePosition good = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(broken, good));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY"))
                .thenReturn(new BigDecimal("120"));
        when(summaryFootprintBuilder.openDerivativeNotionalTry(good)).thenReturn(new BigDecimal("120"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        // The broken lot contributes nothing; only the good lot drives the figures.
        assertThat(result.lotCount()).isEqualTo(1);
        assertThat(result.totalEntryValueTry()).isEqualByComparingTo("100.0000");
        assertThat(result.totalPnlTry()).isEqualByComparingTo("20.0000");
        verify(summaryFootprintBuilder, never()).openDerivativeNotionalTry(broken);
    }

    @Test
    void should_holdPnlAtZeroAndCurrentPriceAtZero_when_liveUnitPriceUnavailable() {
        DerivativePosition openLong = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(openLong));
        // Live FX missing => resolver returns null => realizedOrUnrealizedPnl(null) is null => PnL held at 0.
        when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY")).thenReturn(null);
        when(summaryFootprintBuilder.openDerivativeNotionalTry(openLong)).thenReturn(new BigDecimal("100"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL, null);

        assertThat(result.lotCount()).isEqualTo(1);
        assertThat(result.totalPnlTry()).isEqualByComparingTo("0.0000");
        assertThat(result.totalMarketValueTry()).isEqualByComparingTo("100.0000"); // entry notional only, pnl 0
        assertThat(result.currentPriceTry()).isEqualByComparingTo("0");            // no live price -> orElse(ZERO)
        assertThat(result.pnlPercent()).isEqualByComparingTo("0");
    }

    @Test
    void should_matchSymbolCaseInsensitively_when_assetCodeDiffersInCase() {
        DerivativePosition longLot = openLot(DerivativeDirection.LONG, "100", "1", SYMBOL, "120");
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(longLot));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("120"), "TRY"))
                .thenReturn(new BigDecimal("120"));
        when(summaryFootprintBuilder.openDerivativeNotionalTry(longLot)).thenReturn(new BigDecimal("120"));
        when(multiCurrencyPnlCalculator.computeFromFootprints(any(), any(), any(), any(), any()))
                .thenReturn(STUB_FRAMES);

        AssetAggregateResponse result = service.viopAssetAggregate(PORTFOLIO_ID, SYMBOL.toLowerCase(), null);

        assertThat(result.lotCount()).isEqualTo(1);
        assertThat(result.totalPnlTry()).isEqualByComparingTo("20.0000");
    }

    // --- helpers ----------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<RealReturnCalculator.EntryFootprint>> footprintCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private static ViopContract tryContract(String symbol, String lastPrice) {
        // Symbol ends in TRY before the expiry, so resolvePriceCurrency() == TRY and convertLiveToTry passes
        // the native price straight through; contractSize 1 keeps notional == price × lots.
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .contractSize(BigDecimal.ONE)
                .initialMargin(new BigDecimal("3500.00"))
                .currency("TRY")
                .lastPrice(new BigDecimal(lastPrice))
                .active(true)
                .build();
    }

    private static DerivativePosition openLot(DerivativeDirection direction, String entryPrice,
            String quantityLot, String symbol, String lastPrice) {
        return DerivativePosition.builder()
                .id(idFor(direction, false))
                .direction(direction)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal(entryPrice))
                .quantityLot(new BigDecimal(quantityLot))
                .viopContract(tryContract(symbol, lastPrice))
                .build();
    }

    private static DerivativePosition closedLot(DerivativeDirection direction, String entryPrice,
            String quantityLot, String symbol, String closePrice) {
        DerivativePosition lot = DerivativePosition.builder()
                .id(idFor(direction, true))
                .direction(direction)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal(entryPrice))
                .quantityLot(new BigDecimal(quantityLot))
                .viopContract(tryContract(symbol, "120"))
                .build();
        lot.closeFull(LocalDate.of(2026, 5, 1), new BigDecimal(closePrice));
        return lot;
    }

    private static long idFor(DerivativeDirection direction, boolean closed) {
        long base = direction == DerivativeDirection.LONG ? 10L : 20L;
        return closed ? base + 1 : base;
    }
}
