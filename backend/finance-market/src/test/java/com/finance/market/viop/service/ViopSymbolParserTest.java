package com.finance.market.viop.service;

import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopExerciseStyle;
import com.finance.market.viop.model.ViopOptionSide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ViopSymbolParserTest {

    private final ViopSymbolParser parser = new ViopSymbolParser();

    @ParameterizedTest
    @CsvSource({
            "O_AKBNKE0526P45.00, AKBNK, EUROPEAN, 2026, 5, PUT, 45.00",
            "O_TOASOE0526P290.00, TOASO, EUROPEAN, 2026, 5, PUT, 290.00",
            "O_ALARKE0526C100.00, ALARK, EUROPEAN, 2026, 5, CALL, 100.00"
    })
    void should_parseOptionSymbol_when_formatMatches(String symbol, String underlying, String style,
                                                      int year, int month, String side, String strike) {
        ViopSymbolParser.Parsed parsed = parser.parse(symbol);

        assertThat(parsed).isNotNull();
        assertThat(parsed.kind()).isEqualTo(ViopContractKind.OPTION);
        assertThat(parsed.underlying()).isEqualTo(underlying);
        assertThat(parsed.exerciseStyle()).isEqualTo(ViopExerciseStyle.valueOf(style));
        assertThat(parsed.expiryYear()).isEqualTo(year);
        assertThat(parsed.expiryMonth()).isEqualTo(month);
        assertThat(parsed.optionSide()).isEqualTo(ViopOptionSide.valueOf(side));
        assertThat(parsed.strikePrice().toPlainString()).isEqualTo(strike);
    }

    @ParameterizedTest
    @CsvSource({
            "F_USDTRY0626, USDTRY, 2026, 6",
            "F_XU0300626, XU030, 2026, 6"
    })
    void should_parseFutureSymbol_when_formatMatches(String symbol, String underlying, int year, int month) {
        ViopSymbolParser.Parsed parsed = parser.parse(symbol);

        assertThat(parsed).isNotNull();
        assertThat(parsed.kind()).isEqualTo(ViopContractKind.FUTURE);
        assertThat(parsed.underlying()).isEqualTo(underlying);
        assertThat(parsed.expiryYear()).isEqualTo(year);
        assertThat(parsed.expiryMonth()).isEqualTo(month);
        assertThat(parsed.optionSide()).isNull();
        assertThat(parsed.strikePrice()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "ABC_DEF", "X_AKBNK0526", "O_AKBNK_X0526P45.00"})
    void should_returnNull_when_symbolFormatNotRecognised(String symbol) {
        assertThat(parser.parse(symbol)).isNull();
    }

    @Test
    void should_returnNull_when_symbolIsNull() {
        assertThat(parser.parse(null)).isNull();
    }

    @Test
    void should_returnLastDayOfMonth_when_computingImpliedExpiry() {
        assertThat(parser.impliedExpiry(2026, 5)).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(parser.impliedExpiry(2026, 2)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(parser.impliedExpiry(2024, 2)).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(parser.impliedExpiry(2026, 6)).isEqualTo(LocalDate.of(2026, 6, 30));
    }
}
