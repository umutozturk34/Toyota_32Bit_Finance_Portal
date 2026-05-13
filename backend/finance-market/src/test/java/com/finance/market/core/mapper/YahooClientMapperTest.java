package com.finance.market.core.mapper;

import com.finance.common.config.AppProperties;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import com.finance.market.core.dto.internal.YahooChartFullResult;
import com.finance.market.core.dto.internal.YahooChartResponse.Indicators;
import com.finance.market.core.dto.internal.YahooChartResponse.Meta;
import com.finance.market.core.dto.internal.YahooChartResponse.Quote;
import com.finance.market.core.dto.internal.YahooChartResponse.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YahooClientMapperTest {

    private YahooClientMapper mapper;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setTimezone("UTC");
        mapper = new YahooClientMapper(props);
    }

    private Meta meta(BigDecimal price, BigDecimal previousClose, BigDecimal dayHigh,
                     BigDecimal dayLow, Long volume) {
        return new Meta(price, null, previousClose, null, null, dayHigh, dayLow, volume,
                "Long", "Short", "NASDAQ", "USD");
    }

    private Quote quote(List<BigDecimal> open, List<BigDecimal> high, List<BigDecimal> low,
                       List<BigDecimal> close, List<Long> volume) {
        return new Quote(open, high, low, close, volume);
    }

    private Result result(Meta meta, List<Long> timestamps, Quote quote) {
        return new Result(meta, timestamps, new Indicators(List.of(quote)));
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private long epochSec(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 0, 0).toEpochSecond(ZoneOffset.UTC);
    }

    @Test
    void toQuoteDto_mapsMetaFieldsDirectly_andResolvesPreviousCloseFromMeta() {
        Result res = result(meta(bd(150), bd(148), bd(152), bd(149), 1000L),
                List.of(epochSec(2026, 5, 12)),
                quote(List.of(bd(149)), List.of(bd(151)), List.of(bd(148)), List.of(bd(150)),
                        List.of(1000L)));

        YahooQuoteDto dto = mapper.toQuoteDto(res);

        assertThat(dto.regularMarketPrice()).isEqualByComparingTo(bd(150));
        assertThat(dto.previousClose()).isEqualByComparingTo(bd(148));
        assertThat(dto.openPrice()).isEqualByComparingTo(bd(149));
        assertThat(dto.dayHigh()).isEqualByComparingTo(bd(152));
        assertThat(dto.dayLow()).isEqualByComparingTo(bd(149));
        assertThat(dto.volume()).isEqualTo(1000L);
    }

    @Test
    void toQuoteDto_fallsBackToPreviousCandleClose_whenMetaPreviousCloseNull() {
        Result res = result(meta(bd(150), null, bd(152), bd(149), 1000L),
                List.of(epochSec(2026, 5, 11), epochSec(2026, 5, 12)),
                quote(List.of(bd(148), bd(149)), List.of(bd(150), bd(151)),
                        List.of(bd(147), bd(148)), List.of(bd(149), bd(150)),
                        List.of(900L, 1000L)));

        YahooQuoteDto dto = mapper.toQuoteDto(res);

        assertThat(dto.previousClose()).isEqualByComparingTo(bd(149));
    }

    @Test
    void toQuoteDto_returnsNullPreviousClose_whenNoCloseHistoryAvailable() {
        Result res = result(meta(bd(150), null, null, null, null), List.of(epochSec(2026, 5, 12)),
                quote(List.of(bd(149)), List.of(bd(151)), List.of(bd(148)), Arrays.asList((BigDecimal) null),
                        null));

        YahooQuoteDto dto = mapper.toQuoteDto(res);

        assertThat(dto.previousClose()).isNull();
    }

    @Test
    void toCandleDtos_returnsEmpty_whenTimestampsMissing() {
        Result res = result(meta(bd(150), bd(148), null, null, null), null,
                quote(List.of(), List.of(), List.of(), List.of(), List.of()));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, false);

        assertThat(candles).isEmpty();
    }

    @Test
    void toCandleDtos_returnsEmpty_whenQuoteIsNull() {
        Result res = new Result(meta(bd(150), bd(148), null, null, null),
                List.of(epochSec(2026, 5, 12)), new Indicators(List.of()));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, false);

        assertThat(candles).isEmpty();
    }

    @Test
    void toCandleDtos_skipsCandle_whenAllOhlcAreNull() {
        Result res = result(meta(bd(150), null, null, null, null),
                List.of(epochSec(2026, 5, 11), epochSec(2026, 5, 12)),
                quote(Arrays.asList(null, bd(149)),
                        Arrays.asList(null, bd(151)),
                        Arrays.asList(null, bd(148)),
                        Arrays.asList(null, bd(150)),
                        Arrays.asList(null, 1000L)));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, false);

        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).close()).isEqualByComparingTo(bd(150));
    }

    @Test
    void toCandleDtos_fallsBackToMarketPrice_whenCloseNullButOtherFieldsPresent() {
        Result res = result(meta(bd(155), null, null, null, null),
                List.of(epochSec(2026, 5, 12)),
                quote(List.of(bd(149)), List.of(bd(151)), List.of(bd(148)),
                        Arrays.asList((BigDecimal) null), List.of(1000L)));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, false);

        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).close()).isEqualByComparingTo(bd(155));
    }

    @Test
    void toCandleDtos_skipsCandle_whenCloseNullAndNoMarketPriceFallback() {
        Result res = result(new Meta(null, null, null, null, null, null, null, null, null, null, null, null),
                List.of(epochSec(2026, 5, 12)),
                quote(List.of(bd(149)), List.of(bd(151)), List.of(bd(148)),
                        Arrays.asList((BigDecimal) null), List.of(1000L)));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, false);

        assertThat(candles).isEmpty();
    }

    @Test
    void toCandleDtos_propagatesCloseToMissingOhlc_andTruncatesToDays() {
        long ts = LocalDateTime.of(2026, 5, 12, 14, 30).toEpochSecond(ZoneOffset.UTC);
        Result res = result(meta(bd(150), null, null, null, null),
                List.of(ts),
                quote(Arrays.asList((BigDecimal) null),
                        Arrays.asList((BigDecimal) null),
                        Arrays.asList((BigDecimal) null),
                        List.of(bd(150)),
                        List.of(1000L)));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, true);

        assertThat(candles).hasSize(1);
        YahooCandleDto c = candles.get(0);
        assertThat(c.open()).isEqualByComparingTo(bd(150));
        assertThat(c.high()).isEqualByComparingTo(bd(150));
        assertThat(c.low()).isEqualByComparingTo(bd(150));
        assertThat(c.candleDate()).isEqualTo(LocalDateTime.of(2026, 5, 12, 0, 0));
    }

    @Test
    void toCandleDtos_keepsIntradayTimestamp_whenTruncateFalse() {
        long ts = LocalDateTime.of(2026, 5, 12, 14, 30).toEpochSecond(ZoneOffset.UTC);
        Result res = result(meta(bd(150), null, null, null, null),
                List.of(ts),
                quote(List.of(bd(149)), List.of(bd(151)), List.of(bd(148)), List.of(bd(150)),
                        List.of(1000L)));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, false);

        assertThat(candles.get(0).candleDate()).isEqualTo(LocalDateTime.of(2026, 5, 12, 14, 30));
    }

    @Test
    void toCandleDtos_handlesMissingVolumeList_byEmittingNullVolume() {
        Result res = result(meta(bd(150), null, null, null, null),
                List.of(epochSec(2026, 5, 12)),
                quote(List.of(bd(149)), List.of(bd(151)), List.of(bd(148)), List.of(bd(150)),
                        null));

        List<YahooCandleDto> candles = mapper.toCandleDtos(res, false);

        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).volume()).isNull();
    }

    @Test
    void toFullResult_combinesQuoteDtoAndCandleDtos() {
        Result res = result(meta(bd(150), bd(148), bd(152), bd(149), 1000L),
                List.of(epochSec(2026, 5, 12)),
                quote(List.of(bd(149)), List.of(bd(151)), List.of(bd(148)), List.of(bd(150)),
                        List.of(1000L)));

        YahooChartFullResult<YahooQuoteDto> full = mapper.toFullResult(res, false);

        assertThat(full.quote().regularMarketPrice()).isEqualByComparingTo(bd(150));
        assertThat(full.candles()).hasSize(1);
        assertThat(full.candles().get(0).close()).isEqualByComparingTo(bd(150));
    }
}
