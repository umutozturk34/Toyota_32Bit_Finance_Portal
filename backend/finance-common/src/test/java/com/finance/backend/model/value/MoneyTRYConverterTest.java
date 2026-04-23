package com.finance.backend.model.value;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTRYConverterTest {

    private final MoneyTRYConverter converter = new MoneyTRYConverter();

    @Test
    void convertToDatabaseColumnReturnsUnderlyingAmount() {
        MoneyTRY money = MoneyTRY.of("125.4567");

        BigDecimal column = converter.convertToDatabaseColumn(money);

        assertThat(column).isEqualByComparingTo("125.4567");
    }

    @Test
    void convertToDatabaseColumnReturnsNullForNullMoney() {
        BigDecimal column = converter.convertToDatabaseColumn(null);

        assertThat(column).isNull();
    }

    @Test
    void convertToEntityAttributeWrapsColumnValue() {
        BigDecimal column = new BigDecimal("250.1234");

        MoneyTRY money = converter.convertToEntityAttribute(column);

        assertThat(money.amount()).isEqualByComparingTo("250.1234");
    }

    @Test
    void convertToEntityAttributeReturnsNullForNullColumn() {
        MoneyTRY money = converter.convertToEntityAttribute(null);

        assertThat(money).isNull();
    }

    @Test
    void roundTripPreservesAmountAtScaleFour() {
        MoneyTRY original = MoneyTRY.of("99.9999");

        BigDecimal column = converter.convertToDatabaseColumn(original);
        MoneyTRY restored = converter.convertToEntityAttribute(column);

        assertThat(restored).isEqualTo(original);
    }
}
