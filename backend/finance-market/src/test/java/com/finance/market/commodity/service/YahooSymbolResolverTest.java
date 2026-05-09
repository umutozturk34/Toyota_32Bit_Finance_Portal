package com.finance.market.commodity.service;

import com.finance.market.commodity.config.CommodityProperties;
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
        CommodityProperties commodityProps = new CommodityProperties();
        commodityProps.setYahooSymbolOverrides(Map.of(
                "XAUTRY", "GC=F",
                "XAGTRY", "SI=F",
                "XPTTRY", "PL=F",
                "XPDTRY", "PA=F"
        ));
        resolver = new YahooSymbolResolver(commodityProps);
    }

    @ParameterizedTest
    @CsvSource({
            "XAUTRY,GC=F",
            "XAGTRY,SI=F",
            "XPTTRY,PL=F",
            "XPDTRY,PA=F"
    })
    void resolveReturnsYahooSymbolForDomainCode(String domain, String yahoo) {
        String resolved = resolver.resolve(domain);

        assertThat(resolved).isEqualTo(yahoo);
    }

    @Test
    void resolveReturnsInputWhenAlreadyYahooFutures() {
        String resolved = resolver.resolve("GC=F");

        assertThat(resolved).isEqualTo("GC=F");
    }

    @Test
    void resolveReturnsNullForUnknownCode() {
        String resolved = resolver.resolve("UNKNOWN");

        assertThat(resolved).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "GC=F,XAUTRY",
            "SI=F,XAGTRY",
            "PL=F,XPTTRY",
            "PA=F,XPDTRY"
    })
    void resolveByYahooSymbolReturnsDomainCode(String yahoo, String domain) {
        Optional<String> resolved = resolver.resolveByYahooSymbol(yahoo);

        assertThat(resolved).contains(domain);
    }

    @ParameterizedTest
    @CsvSource({
            "gc=f,XAUTRY",
            "  GC=F  ,XAUTRY"
    })
    void resolveByYahooSymbolNormalizesInput(String input, String domain) {
        Optional<String> resolved = resolver.resolveByYahooSymbol(input);

        assertThat(resolved).contains(domain);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "XAUTRY", "UNKNOWN=F"})
    void resolveByYahooSymbolReturnsEmptyForNonYahooInputs(String input) {
        Optional<String> resolved = resolver.resolveByYahooSymbol(input);

        assertThat(resolved).isEqualTo(Optional.empty());
    }

    @Test
    void normalizeUppercasesAndTrims() {
        String normalized = resolver.normalize("  xautry  ");

        assertThat(normalized).isEqualTo("XAUTRY");
    }
}
