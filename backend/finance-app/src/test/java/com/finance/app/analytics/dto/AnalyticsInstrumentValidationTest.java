package com.finance.app.analytics.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the instrument-code pattern: commodity futures codes carry '=' (Brent BZ=F, wheat ZW=F, copper HG=F),
 * which previously failed validation and made those instruments 400 in scenarios. Valid codes must pass; codes
 * with whitespace/quotes/slashes (injection-shaped) must still be rejected.
 */
class AnalyticsInstrumentValidationTest {

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

    @ParameterizedTest
    @ValueSource(strings = {"BZ=F", "ZW=F", "HG=F", "XAUTRY", "THYAO.IS", "TP.TRYTAS.MT02", "USD", "bitcoin"})
    void accepts_validInstrumentCodes(String code) {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.COMMODITY, code);

        assertThat(validator.validate(instrument)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BZ F", "a/b", "x'y", "drop table", "BZ;F", ""})
    void rejects_invalidInstrumentCodes(String code) {
        AnalyticsInstrument instrument = new AnalyticsInstrument(AnalyticsInstrumentType.COMMODITY, code);

        assertThat(validator.validate(instrument)).isNotEmpty();
    }
}
