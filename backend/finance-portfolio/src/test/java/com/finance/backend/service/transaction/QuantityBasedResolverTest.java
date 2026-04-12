package com.finance.backend.service.transaction;

import com.finance.backend.exception.BadRequestException;
import com.finance.backend.model.AssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityBasedResolverTest {

    private final QuantityBasedResolver resolver = new QuantityBasedResolver();

    @ParameterizedTest
    @EnumSource(value = AssetType.class, names = {"STOCK", "FUND"})
    void supportsStockAndFund(AssetType type) {
        assertThat(resolver.supports(type)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = AssetType.class, names = {"CRYPTO", "FOREX"})
    void doesNotSupportCryptoAndForex(AssetType type) {
        assertThat(resolver.supports(type)).isFalse();
    }

    @Test
    void resolveSetsQuantityToScale8AndCalculatesTotalCost() {
        ResolvedInput result = resolver.resolve(new BigDecimal("10"), null, new BigDecimal("45.5000"));

        assertThat(result.quantity()).isEqualByComparingTo(new BigDecimal("10.00000000"));
        assertThat(result.totalCostTry()).isEqualByComparingTo(new BigDecimal("455.0000"));
    }

    @Test
    void resolveLargeQuantityMultipliesCorrectly() {
        ResolvedInput result = resolver.resolve(new BigDecimal("500"), null, new BigDecimal("123.4567"));

        assertThat(result.quantity()).isEqualByComparingTo(new BigDecimal("500.00000000"));
        assertThat(result.totalCostTry()).isEqualByComparingTo(new BigDecimal("61728.3500"));
    }

    @ParameterizedTest
    @CsvSource(value = {"null", "0", "-5"}, nullValues = "null")
    void invalidQuantityThrowsMiktarError(String qty) {
        BigDecimal quantity = qty == null ? null : new BigDecimal(qty);

        assertThatThrownBy(() -> resolver.resolve(quantity, null, new BigDecimal("100")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Miktar belirtilmelidir");
    }

    @Test
    void fractionalQuantityThrows() {
        assertThatThrownBy(() -> resolver.resolve(new BigDecimal("2.5"), null, new BigDecimal("100")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tam adet");
    }

    @Test
    void amountTryIsIgnoredWhenQuantityProvided() {
        ResolvedInput result = resolver.resolve(new BigDecimal("3"), new BigDecimal("99999"), new BigDecimal("50.0000"));

        assertThat(result.quantity()).isEqualByComparingTo(new BigDecimal("3.00000000"));
        assertThat(result.totalCostTry()).isEqualByComparingTo(new BigDecimal("150.0000"));
    }
}
