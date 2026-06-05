package com.finance.portfolio.service.summary;

import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.service.pricing.DerivativePricingResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for {@link DerivativeAggregationService}: the open/closed single-pass fold and the
 * closed-only realised-cash total. Lives in the production sub-package so it can read the package-private
 * {@link DerivativeTotals} accumulator. {@link DerivativePricingResolver} is mocked so each branch's live
 * FX outcome (present / missing) is driven deterministically, while {@link DerivativePosition} /
 * {@link ViopContract} are built with their real builders to keep the direction-aware PnL math honest.
 */
@ExtendWith(MockitoExtension.class)
class DerivativeAggregationServiceTest {

    @Mock private DerivativePositionRepository derivativePositionRepository;
    @Mock private DerivativePricingResolver derivativePricingResolver;

    @InjectMocks private DerivativeAggregationService service;

    private static final Long PORTFOLIO_ID = 1L;
    private static final BigDecimal SIZE = new BigDecimal("1000");

    private ViopContract contract(String symbol, String currency, BigDecimal contractSize, BigDecimal lastPrice) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .category(ViopCategory.CURRENCY_FUTURE_TRY)
                .contractSize(contractSize)
                .initialMargin(new BigDecimal("3500.00"))
                .currency(currency)
                .lastPrice(lastPrice)
                .active(true)
                .build();
    }

    private DerivativePosition openPosition(DerivativeDirection direction, BigDecimal entryPrice,
                                            BigDecimal qty, ViopContract contract) {
        return DerivativePosition.builder()
                .id(10L)
                .direction(direction)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(entryPrice)
                .quantityLot(qty)
                .viopContract(contract)
                .build();
    }

    @Test
    void should_value_open_long_at_current_notional_with_direction_aware_pnl_when_live_fx_present() {
        // Arrange: LONG entry 35.20, live 35.50 (TRY), size 1000, lot 1. Live FX present.
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("35.50"));
        DerivativePosition open = openPosition(DerivativeDirection.LONG, new BigDecimal("35.20"), BigDecimal.ONE, c);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(open));
        when(derivativePricingResolver.convertLiveToTry(any(), any())).thenReturn(new BigDecimal("35.50"));

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: MV = 35.50 * 1000 * 1; entry notional = 35.20 * 1000; PnL = (35.50-35.20)*1000 = +300.
        assertThat(totals.openMarketValue).isEqualByComparingTo("35500");
        assertThat(totals.openEntryNotional).isEqualByComparingTo("35200");
        assertThat(totals.openPnl).isEqualByComparingTo("300");
        assertThat(totals.closedExitValue).isEqualByComparingTo("0");
        assertThat(totals.closedEntryNotional).isEqualByComparingTo("0");
    }

    @Test
    void should_track_positive_pnl_but_falling_notional_for_open_short_when_live_fx_present() {
        // Arrange: SHORT entry 35.20, live 34.20, size 1000, lot 1 — a profiting short (notional falls).
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("34.20"));
        DerivativePosition open = openPosition(DerivativeDirection.SHORT, new BigDecimal("35.20"), BigDecimal.ONE, c);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(open));
        when(derivativePricingResolver.convertLiveToTry(any(), any())).thenReturn(new BigDecimal("34.20"));

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: MV is the current (lower) notional 34200, while direction-aware PnL is positive (+1000):
        // value − cost (34200 − 35200 = −1000) ≠ PnL (+1000) for a SHORT.
        assertThat(totals.openMarketValue).isEqualByComparingTo("34200");
        assertThat(totals.openEntryNotional).isEqualByComparingTo("35200");
        assertThat(totals.openPnl).isEqualByComparingTo("1000");
    }

    @Test
    void should_fall_back_to_entry_notional_and_hold_pnl_zero_when_live_fx_missing() {
        // Arrange: contract has no last price -> resolver consulted for candle close, then convertLiveToTry
        // returns null (FX missing). The lot must value at entry notional and contribute zero PnL.
        ViopContract c = contract("F_XU030USD0626", "USD", BigDecimal.ONE, null);
        DerivativePosition open = openPosition(DerivativeDirection.LONG, new BigDecimal("3000"), BigDecimal.ONE, c);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(open));
        when(derivativePricingResolver.latestCandleClose(anyString())).thenReturn(new BigDecimal("50"));
        when(derivativePricingResolver.convertLiveToTry(any(), any())).thenReturn(null);

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: MV falls back to entry notional 3000*1*1; PnL stays 0 because exit price is null.
        assertThat(totals.openMarketValue).isEqualByComparingTo("3000");
        assertThat(totals.openEntryNotional).isEqualByComparingTo("3000");
        assertThat(totals.openPnl).isEqualByComparingTo("0");
    }

    @Test
    void should_default_contract_size_to_one_when_open_lot_contract_size_null() {
        // Arrange: null contract size -> defaults to ONE in both the MV and entry-notional computations.
        ViopContract c = contract("F_USDTRY0626", "TRY", null, new BigDecimal("40"));
        DerivativePosition open = DerivativePosition.builder()
                .id(11L)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("35"))
                .quantityLot(BigDecimal.ONE)
                .viopContract(c)
                .build();
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(open));
        when(derivativePricingResolver.convertLiveToTry(any(), any())).thenReturn(new BigDecimal("40"));

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: size defaulted to ONE -> MV = 40 * 1 * 1 = 40; entry notional = 35 * 1 * 1 = 35.
        assertThat(totals.openMarketValue).isEqualByComparingTo("40");
        assertThat(totals.openEntryNotional).isEqualByComparingTo("35");
    }

    @Test
    void should_skip_position_when_viop_contract_is_null() {
        // Arrange: a position whose contract is null must be skipped entirely (no totals change).
        DerivativePosition noContract = DerivativePosition.builder()
                .id(12L)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("35"))
                .quantityLot(BigDecimal.ONE)
                .viopContract(null)
                .build();
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(noContract));

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: every bucket stays at its zero seed.
        assertThat(totals.openMarketValue).isEqualByComparingTo("0");
        assertThat(totals.openEntryNotional).isEqualByComparingTo("0");
        assertThat(totals.openPnl).isEqualByComparingTo("0");
        assertThat(totals.closedExitValue).isEqualByComparingTo("0");
        assertThat(totals.closedEntryNotional).isEqualByComparingTo("0");
    }

    @Test
    void should_skip_position_when_nominal_exposure_is_null() {
        // Arrange: contract present but entryPrice null -> nominalExposure() == null -> skip.
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("40"));
        DerivativePosition nullExposure = DerivativePosition.builder()
                .id(13L)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(null)
                .quantityLot(BigDecimal.ONE)
                .viopContract(c)
                .build();
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(nullExposure));

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: nothing accumulated.
        assertThat(totals.openMarketValue).isEqualByComparingTo("0");
        assertThat(totals.openEntryNotional).isEqualByComparingTo("0");
        assertThat(totals.openPnl).isEqualByComparingTo("0");
    }

    @Test
    void should_fold_closed_long_lot_as_entry_plus_realized_when_close_price_present() {
        // Arrange: closed LONG entry 35.20 -> close 36.00, size 1000, lot 1. realized = +800.
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("35.50"));
        DerivativePosition closed = openPosition(DerivativeDirection.LONG, new BigDecimal("35.20"), BigDecimal.ONE, c);
        closed.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("36.00"),
                com.finance.portfolio.derivative.model.DerivativeCloseReason.USER_CLOSED);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: closedEntryNotional = |35200|; exit = entryNotional + realized = 35200 + 800 = 36000.
        assertThat(totals.closedEntryNotional).isEqualByComparingTo("35200");
        assertThat(totals.closedExitValue).isEqualByComparingTo("36000");
        assertThat(totals.openMarketValue).isEqualByComparingTo("0");
        assertThat(totals.openEntryNotional).isEqualByComparingTo("0");
        assertThat(totals.openPnl).isEqualByComparingTo("0");
    }

    @Test
    void should_fold_closed_lot_exit_as_entry_notional_when_realized_is_null() {
        // Arrange: closeDate set but closePrice null -> realizedOrUnrealizedPnl returns null -> exit = entry.
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("35.50"));
        DerivativePosition closed = openPosition(DerivativeDirection.LONG, new BigDecimal("35.20"), BigDecimal.ONE, c);
        closed.setCloseDate(LocalDate.of(2026, 5, 1));
        closed.setClosePrice(null);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);

        // Assert: realized null -> exit equals the entry notional, entry bucket also = |entryNotional|.
        assertThat(totals.closedEntryNotional).isEqualByComparingTo("35200");
        assertThat(totals.closedExitValue).isEqualByComparingTo("35200");
    }

    @Test
    void should_total_realized_pnl_across_closed_lots_when_summing_exit_minus_entry() {
        // Arrange: one closed LONG (+800), one closed SHORT (entry 35.20 -> close 34.20 => +1000), and one
        // OPEN lot that must be ignored by the closed-only sum.
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("35.50"));
        DerivativePosition closedLong = openPosition(DerivativeDirection.LONG, new BigDecimal("35.20"), BigDecimal.ONE, c);
        closedLong.closeFull(LocalDate.of(2026, 5, 1), new BigDecimal("36.00"));
        DerivativePosition closedShort = openPosition(DerivativeDirection.SHORT, new BigDecimal("35.20"), BigDecimal.ONE, c);
        closedShort.closeFull(LocalDate.of(2026, 5, 2), new BigDecimal("34.20"));
        DerivativePosition open = openPosition(DerivativeDirection.LONG, new BigDecimal("35.20"), BigDecimal.ONE, c);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID))
                .thenReturn(List.of(closedLong, closedShort, open));

        // Act
        BigDecimal total = service.closedDerivativeExitMinusEntry(PORTFOLIO_ID);

        // Assert: +800 (long) + +1000 (short profits as price falls) = +1800; open lot excluded.
        assertThat(total).isEqualByComparingTo("1800");
    }

    @Test
    void should_skip_open_and_null_realized_lots_when_summing_closed_exit_minus_entry() {
        // Arrange: an open lot (closeDate null -> skipped) and a closed lot with null closePrice
        // (realized null -> not added). Sum must be zero.
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("35.50"));
        DerivativePosition open = openPosition(DerivativeDirection.LONG, new BigDecimal("35.20"), BigDecimal.ONE, c);
        DerivativePosition closedNoPrice = openPosition(DerivativeDirection.LONG, new BigDecimal("35.20"), BigDecimal.ONE, c);
        closedNoPrice.setCloseDate(LocalDate.of(2026, 5, 1));
        closedNoPrice.setClosePrice(null);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID))
                .thenReturn(List.of(open, closedNoPrice));

        // Act
        BigDecimal total = service.closedDerivativeExitMinusEntry(PORTFOLIO_ID);

        // Assert
        assertThat(total).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @CsvSource({
            // direction, entry, close, expectedRealized (size 1000, lot 1)
            "LONG,  35.20, 36.00,  800",
            "LONG,  35.20, 34.20, -1000",
            "SHORT, 35.20, 34.20,  1000",
            "SHORT, 35.20, 36.00, -800",
    })
    void should_compute_direction_aware_closed_realized_when_summing(String direction, BigDecimal entry,
                                                                     BigDecimal close, BigDecimal expected) {
        // Arrange
        ViopContract c = contract("F_USDTRY0626", "TRY", SIZE, new BigDecimal("35.50"));
        DerivativePosition closed = openPosition(DerivativeDirection.valueOf(direction), entry, BigDecimal.ONE, c);
        closed.closeFull(LocalDate.of(2026, 5, 1), close);
        when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(closed));

        // Act
        BigDecimal total = service.closedDerivativeExitMinusEntry(PORTFOLIO_ID);

        // Assert: realised PnL sign follows the direction.
        assertThat(total).isEqualByComparingTo(expected);
    }

    @Test
    void should_return_empty_totals_when_portfolio_has_no_derivatives() {
        // Arrange
        lenient().when(derivativePositionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of());

        // Act
        DerivativeTotals totals = service.openAndClosedDerivativeTotals(PORTFOLIO_ID);
        BigDecimal closed = service.closedDerivativeExitMinusEntry(PORTFOLIO_ID);

        // Assert
        assertThat(totals.openMarketValue).isEqualByComparingTo("0");
        assertThat(totals.closedExitValue).isEqualByComparingTo("0");
        assertThat(closed).isEqualByComparingTo("0");
    }
}
