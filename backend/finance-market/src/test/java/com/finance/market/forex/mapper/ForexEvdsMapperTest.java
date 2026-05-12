package com.finance.market.forex.mapper;

import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.service.ForexSerieMetadata;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ForexEvdsMapperTest {

    private static final int SCALE = 4;

    private final ForexEvdsMapper mapper = new ForexEvdsMapper();
    private final ForexSerieMetadata usdMeta = new ForexSerieMetadata("USD", "US Dollar", "ABD Doları", 1, true);
    private final ForexSerieMetadata jpyMeta = new ForexSerieMetadata("JPY", "Japanese Yen", "Japon Yeni", 100, false);

    @Test
    void should_buildCandlesWithSellingAndBuyingPrices_when_responseHasUsdData() {
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "11-05-2026",
                        "TP_DK_USD_A_YTL", "45.19",
                        "TP_DK_USD_S_YTL", "45.27")));
        Forex forex = Forex.builder().currencyCode("USD").build();

        List<ForexCandle> candles = mapper.toCandles(forex, usdMeta, response, SCALE);

        assertThat(candles).hasSize(1);
        ForexCandle candle = candles.getFirst();
        assertThat(candle.getCandleDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 11));
        assertThat(candle.getSellingPrice()).isEqualByComparingTo("45.2700");
        assertThat(candle.getBuyingPrice()).isEqualByComparingTo("45.1900");
    }

    @Test
    void should_divideByUnit_when_jpyResponse() {
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "11-05-2026",
                        "TP_DK_JPY_A_YTL", "28.7572",
                        "TP_DK_JPY_S_YTL", "28.9476")));
        Forex forex = Forex.builder().currencyCode("JPY").build();

        List<ForexCandle> candles = mapper.toCandles(forex, jpyMeta, response, SCALE);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().getSellingPrice()).isEqualByComparingTo("0.2895");
    }

    @Test
    void should_keepBuyingOnlyRow_when_sellingPriceIsZero() {
        EvdsDataResponse response = new EvdsDataResponse(2, List.of(
                Map.of("Tarih", "10-05-2026",
                        "TP_DK_USD_A_YTL", "45.19",
                        "TP_DK_USD_S_YTL", "0"),
                Map.of("Tarih", "11-05-2026",
                        "TP_DK_USD_A_YTL", "45.19",
                        "TP_DK_USD_S_YTL", "45.27")));
        Forex forex = Forex.builder().currencyCode("USD").build();

        List<ForexCandle> candles = mapper.toCandles(forex, usdMeta, response, SCALE);

        assertThat(candles).hasSize(2);
        assertThat(candles.getFirst().getSellingPrice()).isNull();
        assertThat(candles.getFirst().getBuyingPrice()).isEqualByComparingTo("45.1900");
        assertThat(candles.getLast().getSellingPrice()).isEqualByComparingTo("45.2700");
    }

    @Test
    void should_skipRow_when_allPricesNullOrZero() {
        EvdsDataResponse response = new EvdsDataResponse(2, List.of(
                Map.of("Tarih", "10-05-2026"),
                Map.of("Tarih", "11-05-2026",
                        "TP_DK_USD_A_YTL", "45.19",
                        "TP_DK_USD_S_YTL", "45.27")));
        Forex forex = Forex.builder().currencyCode("USD").build();

        List<ForexCandle> candles = mapper.toCandles(forex, usdMeta, response, SCALE);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().getCandleDate().toLocalDate())
                .isEqualTo(LocalDate.of(2026, 5, 11));
    }

    @Test
    void should_returnEmptyList_when_responseHasNoItems() {
        EvdsDataResponse response = new EvdsDataResponse(0, List.of());
        Forex forex = Forex.builder().currencyCode("USD").build();

        List<ForexCandle> candles = mapper.toCandles(forex, usdMeta, response, SCALE);

        assertThat(candles).isEmpty();
    }

    @Test
    void should_extractLatestRowWithAllFields_when_responseHasMultipleDays() {
        EvdsDataResponse response = new EvdsDataResponse(3, List.of(
                Map.of("Tarih", "09-05-2026",
                        "TP_DK_USD_A_YTL", "45.10", "TP_DK_USD_S_YTL", "45.18",
                        "TP_DK_USD_A_EF_YTL", "45.05", "TP_DK_USD_S_EF_YTL", "45.25"),
                Map.of("Tarih", "10-05-2026",
                        "TP_DK_USD_A_YTL", "45.15", "TP_DK_USD_S_YTL", "45.22",
                        "TP_DK_USD_A_EF_YTL", "45.10", "TP_DK_USD_S_EF_YTL", "45.30"),
                Map.of("Tarih", "11-05-2026",
                        "TP_DK_USD_A_YTL", "45.19", "TP_DK_USD_S_YTL", "45.27",
                        "TP_DK_USD_A_EF_YTL", "45.15", "TP_DK_USD_S_EF_YTL", "45.33")));

        ForexEvdsMapper.ItemRow latest = mapper.extractLatestRow(response, usdMeta);

        assertThat(latest).isNotNull();
        assertThat(latest.candleDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 5, 11));
        assertThat(latest.buyingRaw()).isEqualByComparingTo("45.19");
        assertThat(latest.sellingRaw()).isEqualByComparingTo("45.27");
        assertThat(latest.effectiveBuyingRaw()).isEqualByComparingTo("45.15");
        assertThat(latest.effectiveSellingRaw()).isEqualByComparingTo("45.33");
    }

    @Test
    void should_returnNullEffective_when_metadataHasNoEfektif() {
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "11-05-2026",
                        "TP_DK_BGN_A_YTL", "26.0", "TP_DK_BGN_S_YTL", "26.5",
                        "TP_DK_BGN_A_EF_YTL", "26.0", "TP_DK_BGN_S_EF_YTL", "26.5")));
        ForexSerieMetadata bgnMeta = new ForexSerieMetadata("BGN", "Bulgarian Lev", "Bulgar Levası", 1, false);

        ForexEvdsMapper.ItemRow latest = mapper.extractLatestRow(response, bgnMeta);

        assertThat(latest.effectiveBuyingRaw()).isNull();
        assertThat(latest.effectiveSellingRaw()).isNull();
    }

    @Test
    void should_returnBuyingOnlyRow_when_extractLatestAndSellingIsZero() {
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "11-05-2026",
                        "TP_DK_USD_A_YTL", "45.19",
                        "TP_DK_USD_S_YTL", "0")));

        ForexEvdsMapper.ItemRow latest = mapper.extractLatestRow(response, usdMeta);

        assertThat(latest).isNotNull();
        assertThat(latest.buyingRaw()).isEqualByComparingTo("45.19");
        assertThat(latest.sellingRaw()).isNull();
    }

    @Test
    void should_returnNull_when_extractLatestAndAllPricesNull() {
        EvdsDataResponse response = new EvdsDataResponse(1, List.of(
                Map.of("Tarih", "11-05-2026")));

        ForexEvdsMapper.ItemRow latest = mapper.extractLatestRow(response, usdMeta);

        assertThat(latest).isNull();
    }

    @Test
    void should_returnEarliestDate_when_responseHasItems() {
        EvdsDataResponse response = new EvdsDataResponse(2, List.of(
                Map.of("Tarih", "10-05-2026", "TP_DK_USD_S_YTL", "45.22"),
                Map.of("Tarih", "11-05-2026", "TP_DK_USD_S_YTL", "45.27")));

        LocalDate earliest = mapper.extractEarliestDate(response);

        assertThat(earliest).isEqualTo(LocalDate.of(2026, 5, 10));
    }
}
