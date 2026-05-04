package com.finance.commodity.service;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.finance.commodity.config.CommodityProperties;
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
