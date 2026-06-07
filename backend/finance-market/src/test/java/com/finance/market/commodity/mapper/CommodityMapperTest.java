package com.finance.market.commodity.mapper;

import com.finance.market.commodity.model.Commodity;
import com.finance.market.commodity.model.CommodityCandle;
import com.finance.market.commodity.model.CommoditySnapshotInput;
import com.finance.market.core.dto.external.YahooCandleDto;
import com.finance.market.core.dto.external.YahooQuoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CommodityMapperTest {

    private CommodityMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(CommodityMapper.class);
    }

    private YahooCandleDto candleDto() {
        return new YahooCandleDto(
                LocalDateTime.of(2026, 5, 1, 0, 0),
                new BigDecimal("3000.0"),
                new BigDecimal("3100.0"),
                new BigDecimal("2950.0"),
                new BigDecimal("3050.0"),
                1000L);
    }

    @Test
    void should_buildCandleFromDtoAndCommodity_when_bothPresent() {
        Commodity commodity = Commodity.builder()
                .commodityCode("GC=F")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 4, 30, 0, 0))
                .build();

        CommodityCandle candle = mapper.toCandleEntity(candleDto(), "GC=F", commodity);

        assertThat(candle.getId()).isNull();
        assertThat(candle.getCommodity()).isSameAs(commodity);
        assertThat(candle.getCommodityCode()).isEqualTo("GC=F");
        assertThat(candle.getCandleDate()).isEqualTo(LocalDateTime.of(2026, 5, 1, 0, 0));
        assertThat(candle.getOpen()).isEqualByComparingTo("3000.0");
        assertThat(candle.getClose()).isEqualByComparingTo("3050.0");
        assertThat(candle.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void should_returnNull_when_allCandleEntityArgumentsAreNull() {
        CommodityCandle candle = mapper.toCandleEntity(null, null, null);

        assertThat(candle).isNull();
    }

    @Test
    void should_overwriteExistingCandleFields_when_updatedWithNewerBar() {
        CommodityCandle existing = CommodityCandle.builder()
                .open(new BigDecimal("1.0"))
                .close(new BigDecimal("1.0"))
                .build();

        mapper.updateCandleEntity(existing, candleDto());

        assertThat(existing.getOpen()).isEqualByComparingTo("3000.0");
        assertThat(existing.getHigh()).isEqualByComparingTo("3100.0");
        assertThat(existing.getLow()).isEqualByComparingTo("2950.0");
        assertThat(existing.getClose()).isEqualByComparingTo("3050.0");
        assertThat(existing.getCandleDate()).isEqualTo(LocalDateTime.of(2026, 5, 1, 0, 0));
    }

    @Test
    void should_leaveCandleUntouched_when_updateBarIsNull() {
        CommodityCandle existing = CommodityCandle.builder().close(new BigDecimal("1.0")).build();

        mapper.updateCandleEntity(existing, null);

        assertThat(existing.getClose()).isEqualByComparingTo("1.0");
    }

    @Test
    void should_buildSnapshotFromQuoteAndTodayAndPreviousCandle_when_previousCandlePresent() {
        YahooQuoteDto quote = new YahooQuoteDto(
                new BigDecimal("3050.0"), new BigDecimal("3000.0"),
                new BigDecimal("3010.0"), new BigDecimal("3100.0"), new BigDecimal("2950.0"),
                1000L, new BigDecimal("50.0"), new BigDecimal("1.67"));
        YahooCandleDto today = candleDto();
        YahooCandleDto previous = new YahooCandleDto(
                LocalDateTime.of(2026, 4, 30, 0, 0),
                new BigDecimal("2900.0"), new BigDecimal("2950.0"), new BigDecimal("2850.0"),
                new BigDecimal("2920.0"), 800L);

        CommoditySnapshotInput input = mapper.toSnapshotInput(quote, today, previous);

        assertThat(input.tryPrice()).isEqualByComparingTo("3050.0");
        assertThat(input.tryPreviousClose()).isEqualByComparingTo("2920.0");
        assertThat(input.usdPrice()).isEqualByComparingTo("3050.0");
        assertThat(input.usdPreviousClose()).isEqualByComparingTo("3000.0");
        assertThat(input.tryOpenPrice()).isEqualByComparingTo("3000.0");
        assertThat(input.tryDayHigh()).isEqualByComparingTo("3100.0");
        assertThat(input.tryDayLow()).isEqualByComparingTo("2950.0");
        assertThat(input.volume()).isEqualTo(1000L);
        assertThat(input.yahooChangePercent()).isEqualByComparingTo("1.67");
    }

    @Test
    void should_leaveTryPreviousCloseNull_when_previousCandleAbsent() {
        YahooQuoteDto quote = new YahooQuoteDto(
                new BigDecimal("3050.0"), new BigDecimal("3000.0"),
                new BigDecimal("3010.0"), new BigDecimal("3100.0"), new BigDecimal("2950.0"),
                1000L, new BigDecimal("50.0"), new BigDecimal("1.67"));

        CommoditySnapshotInput input = mapper.toSnapshotInput(quote, candleDto(), null);

        assertThat(input.tryPreviousClose()).isNull();
        assertThat(input.tryPrice()).isEqualByComparingTo("3050.0");
    }
}
