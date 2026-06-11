package com.finance.portfolio.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the spot-position request bounds added for input validation: asset code/type charset+length,
 * amount caps + decimal scale (quantity up to 6 fraction digits, prices up to 8) and the 3-letter price
 * currency. Valid lots must pass; oversized / wrong-charset / over-precise inputs must be rejected before
 * they reach the entity.
 */
class PositionRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private PositionRequest valid() {
        return new PositionRequest("STOCK", "THYAO.IS", new BigDecimal("10"),
                LocalDateTime.now().minusDays(1), new BigDecimal("100.5"));
    }

    @Test
    void accepts_validPosition() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"THYAO.IS", "BTCUSDT", "BZ=F", "XAUTRY", "TRT140225T15", "bitcoin"})
    void accepts_validAssetCodes(String code) {
        PositionRequest request = new PositionRequest("CRYPTO", code, new BigDecimal("1"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BAD CODE", "code/slash", "co\"de", "drop;table", "way-tooooooooooooooooooooooooo-long"})
    void rejects_badAssetCode(String code) {
        PositionRequest request = new PositionRequest("STOCK", code, new BigDecimal("1"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1"));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "US", "USDD", "TR1"})
    void rejects_badPriceCurrency(String ccy) {
        PositionRequest request = new PositionRequest("STOCK", "THYAO", new BigDecimal("1"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1"), null, null, ccy);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejects_quantityWithTooManyDecimals() {
        PositionRequest request = new PositionRequest("CRYPTO", "BTC", new BigDecimal("1.123456789"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1"));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejects_quantityWithMoreThanSixDecimals() {
        PositionRequest request = new PositionRequest("CRYPTO", "BTC", new BigDecimal("1.1234567"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1"));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void accepts_quantityWithSixDecimals() {
        PositionRequest request = new PositionRequest("CRYPTO", "BTC", new BigDecimal("1.123456"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void accepts_cheapCoinPriceWithEightDecimals() {
        PositionRequest request = new PositionRequest("CRYPTO", "PEPE", new BigDecimal("1000000"),
                LocalDateTime.now().minusDays(1), new BigDecimal("0.00002871"));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejects_priceWithTooManyDecimals() {
        PositionRequest request = new PositionRequest("STOCK", "THYAO", new BigDecimal("1"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1.123456789"));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejects_quantityOverMax() {
        PositionRequest request = new PositionRequest("STOCK", "THYAO", new BigDecimal("10000000000"),
                LocalDateTime.now().minusDays(1), new BigDecimal("1"));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejects_nonPositiveQuantity() {
        PositionRequest request = new PositionRequest("STOCK", "THYAO", BigDecimal.ZERO,
                LocalDateTime.now().minusDays(1), new BigDecimal("1"));

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
