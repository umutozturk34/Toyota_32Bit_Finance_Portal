package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DerivativeSnapshotCalculatorTest {

    private static final Long PORTFOLIO_ID = 7L;
    private static final LocalDateTime BATCH_TS = LocalDateTime.of(2026, 5, 1, 18, 0);
    private static final LocalDate SNAP_DATE = LocalDate.of(2026, 5, 1);

    @Mock
    private DerivativeSnapshotAssembler assembler;

    private DerivativeSnapshotCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DerivativeSnapshotCalculator(assembler);
    }

    private ViopContract derivativeContract(String symbol, BigDecimal contractSize, BigDecimal lastPrice) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .contractSize(contractSize)
                .currency("TRY")
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    private DerivativePosition derivativePosition(ViopContract contract, BigDecimal entry, BigDecimal qty,
                                                  DerivativeDirection direction) {
        return DerivativePosition.builder()
                .id(10L)
                .direction(direction)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(entry)
                .quantityLot(qty)
                .viopContract(contract)
                .build();
    }

    private PortfolioAssetDailySnapshot stubSnapshot() {
        return PortfolioAssetDailySnapshot.builder().build();
    }

    @Test
    void should_returnNull_when_contractIsMissing() {
        DerivativePosition pos = derivativePosition(null, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);

        PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshot(PORTFOLIO_ID, pos, BATCH_TS);

        assertThat(snap).isNull();
        verifyNoInteractions(assembler);
    }

    @Test
    void should_delegateWithClosePriceAndOneFxOverride_when_positionClosedWithClosePrice() {
        ViopContract c = derivativeContract("F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("35.20"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        pos.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("36.00"), DerivativeCloseReason.USER_CLOSED);
        PortfolioAssetDailySnapshot expected = stubSnapshot();
        when(assembler.buildAt(eq(PORTFOLIO_ID), eq(pos), eq(BATCH_TS),
                eq(new BigDecimal("36.00")), eq(BigDecimal.ONE), eq(null))).thenReturn(expected);

        PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshot(PORTFOLIO_ID, pos, BATCH_TS);

        assertThat(snap).isSameAs(expected);
    }

    @Test
    void should_delegateWithLastPriceAndNoFxOverride_when_positionIsOpen() {
        ViopContract c = derivativeContract("F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("35.20"), new BigDecimal("2"),
                DerivativeDirection.LONG);
        PortfolioAssetDailySnapshot expected = stubSnapshot();
        when(assembler.buildAt(eq(PORTFOLIO_ID), eq(pos), eq(BATCH_TS),
                eq(new BigDecimal("35.50")), eq(null), eq(null))).thenReturn(expected);

        PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshot(PORTFOLIO_ID, pos, BATCH_TS);

        assertThat(snap).isSameAs(expected);
    }

    @Test
    void should_useLastPriceFromContract_when_closedWithoutClosePrice() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("99.99"));
        DerivativePosition pos = DerivativePosition.builder()
                .id(11L)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("100"))
                .quantityLot(new BigDecimal("1"))
                .viopContract(c)
                .closeDate(LocalDate.of(2026, 5, 1))
                .build();
        PortfolioAssetDailySnapshot expected = stubSnapshot();
        when(assembler.buildAt(eq(PORTFOLIO_ID), eq(pos), eq(BATCH_TS),
                eq(new BigDecimal("99.99")), eq(null), eq(null))).thenReturn(expected);

        PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshot(PORTFOLIO_ID, pos, BATCH_TS);

        assertThat(snap).isSameAs(expected);
    }

    @Test
    void should_passNullFxAndNullPrior_when_callingTwoArgSnapshotAt() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("100"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        PortfolioAssetDailySnapshot expected = stubSnapshot();
        when(assembler.buildAt(eq(PORTFOLIO_ID), eq(pos), eq(BATCH_TS),
                eq(new BigDecimal("110")), eq(null), eq(null))).thenReturn(expected);

        PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshotAt(PORTFOLIO_ID, pos, BATCH_TS,
                new BigDecimal("110"));

        assertThat(snap).isSameAs(expected);
    }

    @Test
    void should_passFxOverrideAndNullPrior_when_callingThreeArgSnapshotAt() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("100"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        PortfolioAssetDailySnapshot expected = stubSnapshot();
        when(assembler.buildAt(eq(PORTFOLIO_ID), eq(pos), eq(BATCH_TS),
                eq(new BigDecimal("110")), eq(new BigDecimal("32")), eq(null))).thenReturn(expected);

        PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshotAt(PORTFOLIO_ID, pos, BATCH_TS,
                new BigDecimal("110"), new BigDecimal("32"));

        assertThat(snap).isSameAs(expected);
    }

    @Test
    void should_forwardPriorOverride_when_callingFourArgSnapshotAt() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("100"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        PortfolioAssetDailySnapshot prior = stubSnapshot();
        PortfolioAssetDailySnapshot expected = stubSnapshot();
        when(assembler.buildAt(eq(PORTFOLIO_ID), eq(pos), eq(BATCH_TS),
                eq(new BigDecimal("110")), eq(new BigDecimal("32")), eq(prior))).thenReturn(expected);

        PortfolioAssetDailySnapshot snap = calculator.buildDerivativeAssetSnapshotAt(PORTFOLIO_ID, pos, BATCH_TS,
                new BigDecimal("110"), new BigDecimal("32"), prior);

        assertThat(snap).isSameAs(expected);
    }

    @Test
    void should_skipPosition_when_entryDateIsAfterSnapDate() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("100"));
        DerivativePosition pos = DerivativePosition.builder()
                .id(1L)
                .direction(DerivativeDirection.LONG)
                .entryDate(SNAP_DATE.plusDays(1))
                .entryPrice(new BigDecimal("100"))
                .quantityLot(new BigDecimal("1"))
                .viopContract(c)
                .build();
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.totalEntryValue).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.totalMarketValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_skipPosition_when_entryDateIsNull() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("100"));
        DerivativePosition pos = DerivativePosition.builder()
                .id(1L)
                .direction(DerivativeDirection.LONG)
                .entryDate(null)
                .entryPrice(new BigDecimal("100"))
                .quantityLot(new BigDecimal("1"))
                .viopContract(c)
                .build();
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.totalEntryValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_skipPosition_when_contractIsNullDuringAccumulate() {
        DerivativePosition pos = DerivativePosition.builder()
                .id(1L)
                .direction(DerivativeDirection.LONG)
                .entryDate(SNAP_DATE.minusDays(10))
                .entryPrice(new BigDecimal("100"))
                .quantityLot(new BigDecimal("1"))
                .viopContract(null)
                .build();
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.totalEntryValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_skipPosition_when_nominalExposureCannotBeComputed() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("100"));
        DerivativePosition pos = DerivativePosition.builder()
                .id(1L)
                .direction(DerivativeDirection.LONG)
                .entryDate(SNAP_DATE.minusDays(1))
                .entryPrice(null)
                .quantityLot(new BigDecimal("1"))
                .viopContract(c)
                .build();
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.totalEntryValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_addRealizedClose_when_closedStrictlyBeforeSnapDate() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("110"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        pos.closeWith(SNAP_DATE.minusDays(1), new BigDecimal("110"), DerivativeCloseReason.USER_CLOSED);
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.totalEntryValue).isEqualByComparingTo("100");
        assertThat(totals.cumulativeRealized).isEqualByComparingTo("10");
        assertThat(totals.closedExitValue).isEqualByComparingTo("110");
        assertThat(totals.totalMarketValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_useRowMarketValue_when_keyIsPresentAndNotYetCounted() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("110"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        AssetKey key = new AssetKey(MarketType.VIOP, "F_X");
        Map<AssetKey, BigDecimal> rowMvByKey = new HashMap<>();
        rowMvByKey.put(key, new BigDecimal("220"));
        SnapshotTotals totals = new SnapshotTotals();
        Set<AssetKey> counted = new HashSet<>();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, rowMvByKey, totals, counted);

        assertThat(totals.totalMarketValue).isEqualByComparingTo("220");
        assertThat(counted).contains(key);
    }

    @Test
    void should_notDoubleCountSameKey_when_multipleLotsShareSameContract() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("110"));
        DerivativePosition lot1 = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        DerivativePosition lot2 = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        AssetKey key = new AssetKey(MarketType.VIOP, "F_X");
        Map<AssetKey, BigDecimal> rowMvByKey = new HashMap<>();
        rowMvByKey.put(key, new BigDecimal("220"));
        SnapshotTotals totals = new SnapshotTotals();
        Set<AssetKey> counted = new HashSet<>();

        calculator.accumulateDerivativePositions(List.of(lot1, lot2), SNAP_DATE, rowMvByKey, totals, counted);

        assertThat(totals.totalEntryValue).isEqualByComparingTo("200");
        assertThat(totals.totalMarketValue).isEqualByComparingTo("220");
    }

    @Test
    void should_addRealizedClose_when_closedOnSnapDateAndNoRowExists() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("110"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        pos.closeWith(SNAP_DATE, new BigDecimal("110"), DerivativeCloseReason.USER_CLOSED);
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.cumulativeRealized).isEqualByComparingTo("10");
        assertThat(totals.closedExitValue).isEqualByComparingTo("110");
        assertThat(totals.totalMarketValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_doNothingForMarket_when_openButNoRowAndNotClosedOnSnapDate() {
        ViopContract c = derivativeContract("F_X", new BigDecimal("1"), new BigDecimal("110"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("100"), new BigDecimal("1"),
                DerivativeDirection.LONG);
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(pos), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.totalEntryValue).isEqualByComparingTo("100");
        assertThat(totals.totalMarketValue).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totals.cumulativeRealized).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_acceptEmptyList_when_accumulateCalledWithoutPositions() {
        SnapshotTotals totals = new SnapshotTotals();

        calculator.accumulateDerivativePositions(List.of(), SNAP_DATE, new HashMap<>(), totals, new HashSet<>());

        assertThat(totals.totalEntryValue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_returnTrue_when_assetTypeIsNotViop() {
        PortfolioAssetDailySnapshot row = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.STOCK)
                .quantity(BigDecimal.ZERO)
                .build();

        boolean countable = DerivativeSnapshotCalculator.isCountableViopRow(row);

        assertThat(countable).isTrue();
    }

    @Test
    void should_returnTrue_when_viopRowHasNullQuantity() {
        PortfolioAssetDailySnapshot row = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.VIOP)
                .quantity(null)
                .build();

        boolean countable = DerivativeSnapshotCalculator.isCountableViopRow(row);

        assertThat(countable).isTrue();
    }

    @Test
    void should_returnFalse_when_viopRowHasZeroQuantity() {
        PortfolioAssetDailySnapshot row = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.VIOP)
                .quantity(BigDecimal.ZERO)
                .build();

        boolean countable = DerivativeSnapshotCalculator.isCountableViopRow(row);

        assertThat(countable).isFalse();
    }

    @Test
    void should_returnTrue_when_viopRowHasPositiveQuantity() {
        PortfolioAssetDailySnapshot row = PortfolioAssetDailySnapshot.builder()
                .assetType(AssetType.VIOP)
                .quantity(new BigDecimal("3"))
                .build();

        boolean countable = DerivativeSnapshotCalculator.isCountableViopRow(row);

        assertThat(countable).isTrue();
    }

    @Test
    void should_returnTrueForViop_when_isViopAssetTypeReceivesViop() {
        assertThat(DerivativeSnapshotCalculator.isViopAssetType(AssetType.VIOP)).isTrue();
    }

    @Test
    void should_returnFalseForNonViopTypes_when_isViopAssetTypeReceivesOthers() {
        assertThat(DerivativeSnapshotCalculator.isViopAssetType(AssetType.STOCK)).isFalse();
        assertThat(DerivativeSnapshotCalculator.isViopAssetType(AssetType.CRYPTO)).isFalse();
        assertThat(DerivativeSnapshotCalculator.isViopAssetType(AssetType.FUND)).isFalse();
    }

    @Test
    void should_passAllArgumentsExactly_when_buildAssetSnapshotForOpenPositionDelegates() {
        ViopContract c = derivativeContract("F_USDTRY0626", new BigDecimal("1000"), new BigDecimal("35.50"));
        DerivativePosition pos = derivativePosition(c, new BigDecimal("35.20"), new BigDecimal("2"),
                DerivativeDirection.LONG);
        ArgumentCaptor<BigDecimal> priceCaptor = ArgumentCaptor.forClass(BigDecimal.class);

        calculator.buildDerivativeAssetSnapshot(PORTFOLIO_ID, pos, BATCH_TS);

        verify(assembler).buildAt(eq(PORTFOLIO_ID), eq(pos), eq(BATCH_TS),
                priceCaptor.capture(), eq(null), eq(null));
        assertThat(priceCaptor.getValue()).isEqualByComparingTo("35.50");
        verify(assembler, never()).buildAt(any(), any(), any(), any(), eq(BigDecimal.ONE), any());
    }
}
