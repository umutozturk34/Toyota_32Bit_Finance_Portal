package com.finance.backend.service;

import com.finance.backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class YahooSymbolResolverTest {

    private YahooSymbolResolver resolver;

    @BeforeEach
    void setUp() {
        AppProperties.Commodity commodityProps = new AppProperties.Commodity();
        commodityProps.setYahooSymbolOverrides(Map.of(
                "XAUTRY", "GC=F",
                "XAGTRY", "SI=F",
                "XPTTRY", "PL=F",
                "XPDTRY", "PA=F"
        ));
        AppProperties appProperties = new AppProperties();
        appProperties.setCommodity(commodityProps);
        resolver = new YahooSymbolResolver(appProperties);
    }

    @ParameterizedTest
    @CsvSource({
            "XAUTRY,GC=F",
            "XAGTRY,SI=F",
            "XPTTRY,PL=F",
            "XPDTRY,PA=F"
    })
    void resolveReturnsYahooSymbolForDomainCode(String domain, String yahoo) {
        assertThat(resolver.resolve(domain)).isEqualTo(yahoo);
    }

    @Test
    void resolveReturnsInputWhenAlreadyYahooFutures() {
        assertThat(resolver.resolve("GC=F")).isEqualTo("GC=F");
    }

    @Test
    void resolveReturnsNullForUnknownCode() {
        assertThat(resolver.resolve("UNKNOWN")).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "GC=F,XAUTRY",
            "SI=F,XAGTRY",
            "PL=F,XPTTRY",
            "PA=F,XPDTRY"
    })
    void resolveByYahooSymbolReturnsDomainCode(String yahoo, String domain) {
        assertThat(resolver.resolveByYahooSymbol(yahoo)).contains(domain);
    }

    @ParameterizedTest
    @CsvSource({
            "gc=f,XAUTRY",
            "  GC=F  ,XAUTRY"
    })
    void resolveByYahooSymbolNormalizesInput(String input, String domain) {
        assertThat(resolver.resolveByYahooSymbol(input)).contains(domain);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "XAUTRY", "UNKNOWN=F"})
    void resolveByYahooSymbolReturnsEmptyForNonYahooInputs(String input) {
        assertThat(resolver.resolveByYahooSymbol(input)).isEqualTo(Optional.empty());
    }

    @Test
    void normalizeUppercasesAndTrims() {
        assertThat(resolver.normalize("  xautry  ")).isEqualTo("XAUTRY");
    }
}
