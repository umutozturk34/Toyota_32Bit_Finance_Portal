package com.finance.portfolio.derivative.model;

import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerivativePositionPnlTest {

    private static ViopContract contract(String contractSize, String initialMargin) {
        return ViopContract.builder()
                .symbol("F_USDTRY0626")
                .kind(ViopContractKind.FUTURE)
                .contractSize(new BigDecimal(contractSize))
                .initialMargin(initialMargin == null ? null : new BigDecimal(initialMargin))
                .active(true)
                .build();
    }

    private static DerivativePosition positionOf(DerivativeDirection direction,
                                                 String entry, String quantity, ViopContract c) {
        return DerivativePosition.builder()
                .direction(direction)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal(entry))
                .quantityLot(new BigDecimal(quantity))
                .viopContract(c)
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "LONG, 35.20, 35.50, 1000, 2, 600.00",
            "LONG, 35.20, 35.00, 1000, 2, -400.00",
            "SHORT, 35.20, 35.00, 1000, 2, 400.00",
            "SHORT, 35.20, 35.50, 1000, 2, -600.00"
    })
    void pnlReflectsDirection(String direction, String entry, String exit,
                              String contractSize, String quantity, String expectedPnl) {
        ViopContract c = contract(contractSize, null);
        DerivativePosition position = positionOf(DerivativeDirection.valueOf(direction), entry, quantity, c);

        BigDecimal pnl = position.realizedOrUnrealizedPnl(new BigDecimal(exit));

        assertThat(pnl).isEqualByComparingTo(new BigDecimal(expectedPnl));
    }

    @Test
    void closedPositionUsesClosePriceNotCurrentMarket() {
        DerivativePosition position = positionOf(DerivativeDirection.LONG, "100.00", "1", contract("100", null));
        position.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("110.00"), DerivativeCloseReason.USER_CLOSED);

        BigDecimal pnl = position.realizedOrUnrealizedPnl(new BigDecimal("999.99"));

        assertThat(pnl).isEqualByComparingTo("1000.00");
        assertThat(position.isOpen()).isFalse();
    }

    @Test
    void lockedMarginScalesWithQuantity() {
        ViopContract c = contract("100", "5000.00");
        DerivativePosition position = positionOf(DerivativeDirection.LONG, "35.20", "3", c);

        assertThat(position.lockedMargin()).isEqualByComparingTo("15000.00");
    }

    @Test
    void nominalExposureComputed() {
        ViopContract c = contract("1000", "5000.00");
        DerivativePosition position = positionOf(DerivativeDirection.SHORT, "35.20", "2", c);

        assertThat(position.nominalExposure()).isEqualByComparingTo("70400.00");
    }

    @Test
    void closeWithRejectsAlreadyClosed() {
        DerivativePosition position = positionOf(DerivativeDirection.LONG, "100.00", "1", contract("100", null));
        position.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("110.00"), DerivativeCloseReason.USER_CLOSED);

        assertThatThrownBy(() -> position.closeWith(LocalDate.of(2026, 5, 2),
                new BigDecimal("120.00"), DerivativeCloseReason.EXPIRED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pnlFallsBackToSizeOneWhenContractSizeMissing() {
        ViopContract c = ViopContract.builder().symbol("F_X").kind(ViopContractKind.FUTURE).active(true).build();
        DerivativePosition position = positionOf(DerivativeDirection.LONG, "100.00", "1", c);

        assertThat(position.realizedOrUnrealizedPnl(new BigDecimal("110.00")))
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void openPositionWithNullCurrentPriceReturnsNull() {
        DerivativePosition position = positionOf(DerivativeDirection.LONG, "100.00", "1", contract("100", null));

        assertThat(position.realizedOrUnrealizedPnl(null)).isNull();
        assertThat(position.isOpen()).isTrue();
    }

    @Test
    void reopenForUpdateClearsCloseFields() {
        DerivativePosition position = positionOf(DerivativeDirection.LONG, "100.00", "1", contract("100", null));
        position.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("110.00"), DerivativeCloseReason.USER_CLOSED);
        assertThat(position.isOpen()).isFalse();

        position.reopenForUpdate();

        assertThat(position.isOpen()).isTrue();
        assertThat(position.getClosePrice()).isNull();
        assertThat(position.getCloseReason()).isNull();
    }

    @Test
    void updateEntryReplacesDirectionAndAmounts() {
        DerivativePosition position = positionOf(DerivativeDirection.LONG, "100.00", "1", contract("100", null));

        position.updateEntry(DerivativeDirection.SHORT, LocalDate.of(2026, 4, 15),
                new BigDecimal("120.00"), new BigDecimal("3"));

        assertThat(position.getDirection()).isEqualTo(DerivativeDirection.SHORT);
        assertThat(position.getEntryDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(position.getEntryPrice()).isEqualByComparingTo("120.00");
        assertThat(position.getQuantityLot()).isEqualByComparingTo("3");
    }
}
