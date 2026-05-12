package com.finance.market.stock.mapper;

import com.finance.market.core.dto.internal.YahooChartResponse.Indicators;
import com.finance.market.core.dto.internal.YahooChartResponse.Meta;
import com.finance.market.core.dto.internal.YahooChartResponse.Quote;
import com.finance.market.core.dto.internal.YahooChartResponse.Result;
import com.finance.market.stock.dto.external.YahooStockQuoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YahooStockQuoteMapperTest {

    private YahooStockQuoteMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new YahooStockQuoteMapper();
    }

    private Meta meta(BigDecimal price, BigDecimal previousClose, String longName,
                     String shortName, String exchange, String currency, Long volume) {
        return new Meta(price, null, previousClose, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, volume, longName, shortName, exchange, currency);
    }

    private Result result(Meta meta, List<BigDecimal> opens, List<BigDecimal> closes) {
        Quote quote = new Quote(opens, List.of(), List.of(), closes, null);
        return new Result(meta, List.of(1L), new Indicators(List.of(quote)));
    }

    @Test
    void toDto_mapsAllMetaFields_whenPopulated() {
        Meta m = meta(new BigDecimal("100"), new BigDecimal("99"),
                "Apple Inc.", "AAPL", "NASDAQ", "USD", 1000L);
        Result r = result(m, List.of(new BigDecimal("99")), List.of(new BigDecimal("100")));

        YahooStockQuoteDto dto = mapper.toDto(r, "AAPL");

        assertThat(dto.symbol()).isEqualTo("AAPL");
        assertThat(dto.name()).isEqualTo("Apple Inc.");
        assertThat(dto.currentPrice()).isEqualByComparingTo("100");
        assertThat(dto.previousClose()).isEqualByComparingTo("99");
        assertThat(dto.openPrice()).isEqualByComparingTo("99");
        assertThat(dto.exchange()).isEqualTo("NASDAQ");
        assertThat(dto.currency()).isEqualTo("USD");
        assertThat(dto.volume()).isEqualTo(1000L);
    }

    @Test
    void toDto_fallsBackToShortName_whenLongNameNull() {
        Meta m = meta(new BigDecimal("100"), new BigDecimal("99"),
                null, "AAPL", "NASDAQ", "USD", 1000L);
        Result r = result(m, List.of(new BigDecimal("99")), List.of(new BigDecimal("100")));

        YahooStockQuoteDto dto = mapper.toDto(r, "AAPL");

        assertThat(dto.name()).isEqualTo("AAPL");
    }

    @Test
    void toDto_fallsBackToSymbol_whenBothNamesNull() {
        Meta m = meta(new BigDecimal("100"), new BigDecimal("99"),
                null, null, "NASDAQ", "USD", 1000L);
        Result r = result(m, List.of(new BigDecimal("99")), List.of(new BigDecimal("100")));

        YahooStockQuoteDto dto = mapper.toDto(r, "AAPL");

        assertThat(dto.name()).isEqualTo("AAPL");
    }

    @Test
    void toDto_defaultsExchangeToBist_andCurrencyToTry_whenMissing() {
        Meta m = meta(new BigDecimal("100"), new BigDecimal("99"),
                "Apple Inc.", "AAPL", null, null, 1000L);
        Result r = result(m, List.of(new BigDecimal("99")), List.of(new BigDecimal("100")));

        YahooStockQuoteDto dto = mapper.toDto(r, "AAPL");

        assertThat(dto.exchange()).isEqualTo("BIST");
        assertThat(dto.currency()).isEqualTo("TRY");
    }

    @Test
    void toDto_defaultsVolumeToZero_whenMissing() {
        Meta m = meta(new BigDecimal("100"), new BigDecimal("99"),
                "Apple", "AAPL", "NASDAQ", "USD", null);
        Result r = result(m, List.of(new BigDecimal("99")), List.of(new BigDecimal("100")));

        YahooStockQuoteDto dto = mapper.toDto(r, "AAPL");

        assertThat(dto.volume()).isZero();
    }

    @Test
    void toDto_resolvesOpenAndPreviousClose_fromQuoteSeries_whenMetaIncomplete() {
        Meta m = meta(new BigDecimal("100"), null, "Apple", "AAPL", "NASDAQ", "USD", 1000L);
        Result r = result(m,
                Arrays.asList(null, new BigDecimal("99")),
                Arrays.asList(new BigDecimal("98"), new BigDecimal("99.5")));

        YahooStockQuoteDto dto = mapper.toDto(r, "AAPL");

        assertThat(dto.openPrice()).isEqualByComparingTo("99");
        assertThat(dto.previousClose()).isEqualByComparingTo("98");
    }
}
