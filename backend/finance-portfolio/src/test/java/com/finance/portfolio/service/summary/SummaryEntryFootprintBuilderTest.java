package com.finance.portfolio.service.summary;

import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.service.pricing.DerivativePricingResolver;
import com.finance.portfolio.service.pricing.RealReturnCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryEntryFootprintBuilderTest {

    @Mock private DerivativePositionRepository derivativePositionRepository;
    @Mock private DerivativePricingResolver derivativePricingResolver;

    private SummaryEntryFootprintBuilder builder() {
        return new SummaryEntryFootprintBuilder(derivativePositionRepository, derivativePricingResolver);
    }

    // ---- test data factories (mirror PortfolioSummaryServiceTest conventions) ----

    private PortfolioPosition openSpot(BigDecimal qty, BigDecimal entryPrice, LocalDateTime entryDate) {
        return PortfolioPosition.builder()
                .assetType(AssetType.STOCK)
                .assetCode("THYAO.IS")
                .quantity(qty)
                .entryPrice(entryPrice)
                .entryDate(entryDate)
                .build();
    }

    private PortfolioPosition closedSpot(BigDecimal qty, BigDecimal entryPrice,
                                         LocalDateTime entryDate, LocalDateTime exitDate, BigDecimal exitPrice) {
        PortfolioPosition pos = openSpot(qty, entryPrice, entryDate);
        pos.closeWith(exitDate, exitPrice);
        return pos;
    }

    private ViopContract tryContract(String symbol, BigDecimal lastPrice) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .contractSize(new BigDecimal("1000"))
                .initialMargin(new BigDecimal("3500.00"))
                .currency("TRY")
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    private DerivativePosition openDeriv(Long id, DerivativeDirection direction, LocalDate entryDate,
                                         BigDecimal entryPrice, BigDecimal lots, ViopContract contract) {
        return DerivativePosition.builder()
                .id(id)
                .direction(direction)
                .entryDate(entryDate)
                .entryPrice(entryPrice)
                .quantityLot(lots)
                .viopContract(contract)
                .build();
    }

    // ===================== buildEntryFootprints =====================

    @Test
    void should_includeSpotAndAllDerivatives_when_noFilter() {
        // Arrange
        LocalDate entryDay = LocalDate.of(2026, 4, 1);
        PortfolioPosition spot = openSpot(new BigDecimal("10"), new BigDecimal("40"),
                LocalDateTime.of(2026, 3, 1, 12, 0));
        DerivativePosition openLong = openDeriv(50L, DerivativeDirection.LONG, entryDay,
                new BigDecimal("35.20"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("35.50")));
        DerivativePosition closed = openDeriv(51L, DerivativeDirection.LONG, entryDay,
                new BigDecimal("35.20"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("36.00")));
        closed.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("36.00"), DerivativeCloseReason.USER_CLOSED);
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(openLong, closed));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildEntryFootprints(1L, null, List.of(spot));

        // Assert: one spot + one closed-VIOP (kept under Tümü) + one open-VIOP netted LONG
        assertThat(out).hasSize(3);
        RealReturnCalculator.EntryFootprint spotFp = out.get(0);
        assertThat(spotFp.entryDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(spotFp.entryValueTry()).isEqualByComparingTo(new BigDecimal("400"));
        assertThat(out).anySatisfy(fp -> assertThat(fp.exitDate()).isEqualTo(LocalDate.of(2026, 5, 1)));
    }

    @Test
    void should_skipClosedViopButKeepOpen_when_viopOnly() {
        // Arrange
        LocalDate entryDay = LocalDate.of(2026, 4, 1);
        PortfolioPosition spot = openSpot(new BigDecimal("10"), new BigDecimal("40"),
                LocalDateTime.of(2026, 3, 1, 12, 0));
        DerivativePosition openLong = openDeriv(50L, DerivativeDirection.LONG, entryDay,
                new BigDecimal("35.20"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("35.50")));
        DerivativePosition closed = openDeriv(51L, DerivativeDirection.LONG, entryDay,
                new BigDecimal("35.20"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("36.00")));
        closed.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("36.00"), DerivativeCloseReason.USER_CLOSED);
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(openLong, closed));

        // Act: viopOnly → spot dropped, closed VIOP skipped, only the open netted LONG survives
        List<RealReturnCalculator.EntryFootprint> out = builder().buildEntryFootprints(1L, "VIOP", List.of(spot));

        // Assert
        assertThat(out).hasSize(1);
        RealReturnCalculator.EntryFootprint open = out.get(0);
        assertThat(open.exitDate()).isNull();
        assertThat(open.directionSign()).isEqualTo(1);
        assertThat(open.entryDate()).isEqualTo(entryDay);
    }

    @Test
    void should_onlyEmitSpot_when_perTypeFilterIsNonViop() {
        // Arrange: a STOCK filter must NOT touch derivatives at all
        PortfolioPosition spot = openSpot(new BigDecimal("10"), new BigDecimal("40"),
                LocalDateTime.of(2026, 3, 1, 12, 0));
        lenient().when(derivativePositionRepository.findByPortfolioId(1L))
                .thenReturn(List.of(openDeriv(50L, DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                        new BigDecimal("35.20"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("35.50")))));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildEntryFootprints(1L, "STOCK", List.of(spot));

        // Assert
        assertThat(out).hasSize(1);
        assertThat(out.get(0).entryValueTry()).isEqualByComparingTo(new BigDecimal("400"));
    }

    @Test
    void should_skipDerivativesWithNullEntryDateOrNullNotional_when_aggregatingBase() {
        // Arrange: a deriv with null entryDate (skipped) and one with null contract → null nominalExposure (skipped)
        DerivativePosition noEntryDate = openDeriv(60L, DerivativeDirection.LONG, null,
                new BigDecimal("35.20"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("35.50")));
        DerivativePosition noNotional = openDeriv(61L, DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), BigDecimal.ONE, null);
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(noEntryDate, noNotional));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildEntryFootprints(1L, null, List.of());

        // Assert: both filtered, no spot → empty
        assertThat(out).isEmpty();
    }

    @Test
    void should_carryDirectionAwareExit_when_closedShortViopUnderTumu() {
        // Arrange: closed SHORT under Tümü → viopClosed footprint with sign -1, exit date and exit value present
        LocalDate entryDay = LocalDate.of(2026, 4, 1);
        DerivativePosition closedShort = openDeriv(70L, DerivativeDirection.SHORT, entryDay,
                new BigDecimal("40.00"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("38.00")));
        // SHORT entered 40, closed 38 → realized = (40-38)*1000*1 = +2000; entry notional abs = 40000
        closedShort.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("38.00"), DerivativeCloseReason.USER_CLOSED);
        when(derivativePositionRepository.findByPortfolioId(1L)).thenReturn(List.of(closedShort));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildEntryFootprints(1L, null, List.of());

        // Assert
        assertThat(out).hasSize(1);
        RealReturnCalculator.EntryFootprint fp = out.get(0);
        assertThat(fp.entryDate()).isEqualTo(entryDay);
        assertThat(fp.exitDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(fp.entryValueTry()).isEqualByComparingTo(new BigDecimal("40000"));
        assertThat(fp.exitValueTry()).isEqualByComparingTo(new BigDecimal("42000")); // realized +2000 + entryNotional 40000
        assertThat(fp.currentValueTry()).isEqualByComparingTo(new BigDecimal("38000")); // closeNotional 38*1000*1
        assertThat(fp.directionSign()).isEqualTo(-1);
    }

    // ===================== buildSpotEntryFootprints =====================

    @Test
    void should_returnEmpty_when_viopOnlySpotBase() {
        // Arrange
        PortfolioPosition spot = openSpot(new BigDecimal("10"), new BigDecimal("40"),
                LocalDateTime.of(2026, 3, 1, 12, 0));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildSpotEntryFootprints("VIOP", List.of(spot));

        // Assert
        assertThat(out).isEmpty();
    }

    @Test
    void should_carryExitDateAndValue_when_closedSpotUnderNoFilter() {
        // Arrange: closed lot, no filter → kept, carries exit date + exit value
        PortfolioPosition closed = closedSpot(new BigDecimal("10"), new BigDecimal("40"),
                LocalDateTime.of(2026, 3, 1, 12, 0), LocalDateTime.of(2026, 5, 1, 12, 0), new BigDecimal("60"));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildSpotEntryFootprints(null, List.of(closed));

        // Assert: entry 400, realized (60-40)*10 = 200, exit value = 600, exit date frozen
        assertThat(out).hasSize(1);
        RealReturnCalculator.EntryFootprint fp = out.get(0);
        assertThat(fp.entryDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(fp.entryValueTry()).isEqualByComparingTo(new BigDecimal("400"));
        assertThat(fp.exitDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(fp.exitValueTry()).isEqualByComparingTo(new BigDecimal("600"));
    }

    @Test
    void should_dropClosedLotButKeepOpen_when_perTypeFilter() {
        // Arrange: per-type filter is open-only → closed dropped, open kept (with null exit)
        PortfolioPosition open = openSpot(new BigDecimal("10"), new BigDecimal("50"),
                LocalDateTime.of(2026, 3, 1, 12, 0));
        PortfolioPosition closed = closedSpot(new BigDecimal("20"), new BigDecimal("40"),
                LocalDateTime.of(2026, 2, 1, 12, 0), LocalDateTime.of(2026, 4, 1, 12, 0), new BigDecimal("60"));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildSpotEntryFootprints("STOCK", List.of(open, closed));

        // Assert
        assertThat(out).hasSize(1);
        RealReturnCalculator.EntryFootprint fp = out.get(0);
        assertThat(fp.entryValueTry()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(fp.exitDate()).isNull();
        assertThat(fp.exitValueTry()).isNull();
    }

    @Test
    void should_skipLotWithNullEntryDate_when_buildingSpotBase() {
        // Arrange: entryDate null → skipped (entryValue() never reached)
        PortfolioPosition noEntry = openSpot(new BigDecimal("10"), new BigDecimal("40"), null);
        PortfolioPosition valid = openSpot(new BigDecimal("5"), new BigDecimal("20"),
                LocalDateTime.of(2026, 3, 1, 12, 0));

        // Act
        List<RealReturnCalculator.EntryFootprint> out = builder().buildSpotEntryFootprints(null, List.of(noEntry, valid));

        // Assert
        assertThat(out).hasSize(1);
        assertThat(out.get(0).entryValueTry()).isEqualByComparingTo(new BigDecimal("100"));
    }

    // ===================== addNettedOpenViopFootprints =====================

    @Test
    void should_emitOneLongAndOneShortPerGroupPreservingGrossNotional_when_nettingOpenViop() {
        // Arrange: two LONG + one SHORT on the SAME symbol + entry date → grouped into one LONG + one SHORT.
        // TRY-quoted contract with lastPrice set → openDerivativeNotionalTry uses lastPrice directly (no resolver).
        LocalDate entryDay = LocalDate.of(2026, 4, 1);
        ViopContract c1 = tryContract("F_USDTRY0626", new BigDecimal("40")); // long1: live 40
        ViopContract c2 = tryContract("F_USDTRY0626", new BigDecimal("40")); // long2: live 40
        ViopContract c3 = tryContract("F_USDTRY0626", new BigDecimal("40")); // short: live 40
        DerivativePosition long1 = openDeriv(80L, DerivativeDirection.LONG, entryDay,
                new BigDecimal("35"), BigDecimal.ONE, c1);
        DerivativePosition long2 = openDeriv(81L, DerivativeDirection.LONG, entryDay,
                new BigDecimal("36"), BigDecimal.ONE, c2);
        DerivativePosition shortLeg = openDeriv(82L, DerivativeDirection.SHORT, entryDay,
                new BigDecimal("38"), BigDecimal.ONE, c3);
        when(derivativePricingResolver.convertLiveToTry(new BigDecimal("40"), "TRY")).thenReturn(new BigDecimal("40"));
        List<RealReturnCalculator.EntryFootprint> out = new java.util.ArrayList<>();

        // Act
        builder().addNettedOpenViopFootprints(List.of(long1, long2, shortLeg), out);

        // Assert: exactly 2 footprints (one LONG, one SHORT). Gross entry notional preserved.
        // long entry = 35*1000 + 36*1000 = 71000 ; short entry = 38*1000 = 38000 ; current = 40*1000 each.
        assertThat(out).hasSize(2);
        RealReturnCalculator.EntryFootprint longFp = out.stream()
                .filter(f -> f.directionSign() == 1).findFirst().orElseThrow();
        RealReturnCalculator.EntryFootprint shortFp = out.stream()
                .filter(f -> f.directionSign() == -1).findFirst().orElseThrow();
        assertThat(longFp.entryValueTry()).isEqualByComparingTo(new BigDecimal("71000"));
        assertThat(longFp.currentValueTry()).isEqualByComparingTo(new BigDecimal("80000")); // 2 legs × 40×1000
        assertThat(shortFp.entryValueTry()).isEqualByComparingTo(new BigDecimal("38000"));
        assertThat(shortFp.currentValueTry()).isEqualByComparingTo(new BigDecimal("40000"));
        assertThat(longFp.entryDate()).isEqualTo(entryDay);
        assertThat(shortFp.entryDate()).isEqualTo(entryDay);
    }

    @Test
    void should_separateGroups_when_sameSymbolDifferentEntryDates() {
        // Arrange: same symbol but two entry dates → two groups, each emits its own LONG footprint
        DerivativePosition d1 = openDeriv(90L, DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("40")));
        DerivativePosition d2 = openDeriv(91L, DerivativeDirection.LONG, LocalDate.of(2026, 4, 2),
                new BigDecimal("35"), BigDecimal.ONE, tryContract("F_USDTRY0626", new BigDecimal("40")));
        lenient().when(derivativePricingResolver.convertLiveToTry(new BigDecimal("40"), "TRY")).thenReturn(new BigDecimal("40"));
        List<RealReturnCalculator.EntryFootprint> out = new java.util.ArrayList<>();

        // Act
        builder().addNettedOpenViopFootprints(List.of(d1, d2), out);

        // Assert: distinct groups → two LONG footprints on the two distinct entry dates
        assertThat(out).hasSize(2);
        assertThat(out).extracting(RealReturnCalculator.EntryFootprint::entryDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2));
        assertThat(out).allSatisfy(fp -> assertThat(fp.directionSign()).isEqualTo(1));
    }

    // ===================== openDerivativeNotionalTry (fallback paths) =====================

    @Test
    void should_fallBackToCandleClose_when_contractLastPriceIsNull() {
        // Arrange: lastPrice null → resolver.latestCandleClose is consulted; TRY-quoted so convertLiveToTry passes through
        ViopContract c = tryContract("F_USDTRY0626", null);
        DerivativePosition d = openDeriv(95L, DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35"), BigDecimal.ONE, c);
        when(derivativePricingResolver.latestCandleClose("F_USDTRY0626")).thenReturn(new BigDecimal("42"));
        when(derivativePricingResolver.convertLiveToTry(new BigDecimal("42"), "TRY")).thenReturn(new BigDecimal("42"));

        // Act
        BigDecimal current = builder().openDerivativeNotionalTry(d);

        // Assert: 42 × size 1000 × lot 1
        assertThat(current).isEqualByComparingTo(new BigDecimal("42000"));
    }

    @Test
    void should_fallBackToEntryNotional_when_convertLiveToTryReturnsNull() {
        // Arrange: live price present but convertLiveToTry yields null (FX missing) → entry notional abs fallback
        ViopContract c = tryContract("F_USDTRY0626", new BigDecimal("40"));
        DerivativePosition d = openDeriv(96L, DerivativeDirection.SHORT, LocalDate.of(2026, 4, 1),
                new BigDecimal("35"), BigDecimal.ONE, c);
        when(derivativePricingResolver.convertLiveToTry(new BigDecimal("40"), "TRY")).thenReturn(null);

        // Act
        BigDecimal current = builder().openDerivativeNotionalTry(d);

        // Assert: entry notional abs = 35 × 1000 × 1 = 35000
        assertThat(current).isEqualByComparingTo(new BigDecimal("35000"));
    }
}
