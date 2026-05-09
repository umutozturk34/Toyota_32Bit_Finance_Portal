package com.finance.shared.util;

import com.finance.common.exception.BadRequestException;
import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumParserTest {

    @ParameterizedTest
    @CsvSource({
            "STOCK,STOCK",
            "CRYPTO,CRYPTO",
            "FOREX,FOREX",
            "FUND,FUND"
    })
    void parseOrBadRequestReturnsEnumForValidValue(String raw, MarketType expected) {
        assertThat(EnumParser.parseOrBadRequest(MarketType.class, raw, "type"))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stock", "INVALID", "xyz", "123"})
    void parseOrBadRequestThrowsForInvalidValue(String raw) {
        assertThatThrownBy(() -> EnumParser.parseOrBadRequest(MarketType.class, raw, "type"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid type")
                .hasMessageContaining(raw);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void parseOrBadRequestThrowsForBlankOrNull(String raw) {
        assertThatThrownBy(() -> EnumParser.parseOrBadRequest(MarketType.class, raw, "type"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("type is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void parseNullableReturnsNullForBlankOrNull(String raw) {
        assertThat(EnumParser.parseNullable(MarketType.class, raw, "type")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "STOCK,STOCK",
            "CRYPTO,CRYPTO"
    })
    void parseNullableReturnsEnumForValidValue(String raw, MarketType expected) {
        assertThat(EnumParser.parseNullable(MarketType.class, raw, "type"))
                .isEqualTo(expected);
    }

    @Test
    void parseNullableThrowsForInvalidValue() {
        assertThatThrownBy(() -> EnumParser.parseNullable(MarketType.class, "INVALID", "type"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid type: INVALID");
    }
}
