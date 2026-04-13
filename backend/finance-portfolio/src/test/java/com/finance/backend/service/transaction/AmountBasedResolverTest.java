package com.finance.backend.service.transaction;

import com.finance.backend.config.AppProperties;
import com.finance.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountBasedResolverTest {

    private final AmountBasedResolver resolver = new AmountBasedResolver(new AppProperties());

    @Test
    void totalCostIsRecalculatedFromQuantityTimesPrice() {
        BigDecimal amountTry = new BigDecimal("1000");
        BigDecimal unitPrice = new BigDecimal("65432.5678");

        ResolvedInput result = resolver.resolve(null, amountTry, unitPrice);

        BigDecimal expectedQty = amountTry.divide(unitPrice, 8, RoundingMode.DOWN);
        BigDecimal expectedCost = unitPrice.multiply(expectedQty).setScale(4, RoundingMode.HALF_UP);
        assertThat(result.totalCostTry()).isEqualByComparingTo(expectedCost);
        assertThat(result.totalCostTry()).isLessThanOrEqualTo(amountTry);
    }

    @Test
    void quantityRoundedDownPreventsOverspending() {
        ResolvedInput result = resolver.resolve(null, new BigDecimal("100"), new BigDecimal("333.3300"));

        BigDecimal recalculated = new BigDecimal("333.3300").multiply(result.quantity()).setScale(4, RoundingMode.HALF_UP);
        assertThat(result.totalCostTry()).isEqualByComparingTo(recalculated);
        assertThat(result.totalCostTry().compareTo(new BigDecimal("100"))).isLessThanOrEqualTo(0);
    }

    @Test
    void resolveByQuantityPath() {
        ResolvedInput result = resolver.resolve(new BigDecimal("2.5"), null, new BigDecimal("38.1234"));

        assertThat(result.quantity()).isEqualByComparingTo(new BigDecimal("2.50000000"));
        assertThat(result.totalCostTry()).isEqualByComparingTo(new BigDecimal("95.3085"));
    }

    @Test
    void amountTakesPriorityOverQuantity() {
        ResolvedInput result = resolver.resolve(new BigDecimal("1"), new BigDecimal("500"), new BigDecimal("100.0000"));

        assertThat(result.quantity()).isEqualByComparingTo(new BigDecimal("5.00000000"));
    }

    @Test
    void amountBelowMinimumThrows() {
        assertThatThrownBy(() -> resolver.resolve(null, new BigDecimal("9.99"), new BigDecimal("100")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Minimum işlem tutarı");
    }

    @Test
    void quantityResultingBelowMinimumThrows() {
        assertThatThrownBy(() -> resolver.resolve(new BigDecimal("0.001"), null, new BigDecimal("5.0000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Minimum işlem tutarı");
    }

    @Test
    void neitherQuantityNorAmountThrows() {
        assertThatThrownBy(() -> resolver.resolve(null, null, new BigDecimal("100")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Tutar veya miktar belirtilmelidir");
    }
}
