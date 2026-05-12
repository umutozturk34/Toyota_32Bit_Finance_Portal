package com.finance.market.forex.service;

import com.finance.market.core.dto.internal.EvdsSerieResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsForexCurrencyResolverTest {

    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String TODAY = LocalDate.now().format(EVDS_DATE_FMT);

    private final EvdsForexCurrencyResolver resolver = new EvdsForexCurrencyResolver();

    @Test
    void should_extractUsdMetadata_when_dovizSerieParsed() {
        List<EvdsSerieResponse> doviz = List.of(
                new EvdsSerieResponse("TP.DK.USD.A.YTL",
                        "(USD) ABD Doları (Döviz Alış)",
                        "(USD) US Dollar (Buying)",
                        "02-01-1950", TODAY, "GÜNLÜK"));
        List<EvdsSerieResponse> efektif = List.of(
                new EvdsSerieResponse("TP.DK.USD.A.EF.YTL",
                        "(USD) ABD Doları (Efektif Alış)",
                        "(USD) US Dollar (Banknote Buying)",
                        "02-01-1990", TODAY, "GÜNLÜK"));

        List<ForexSerieMetadata> active = resolver.resolveActive(doviz, efektif);

        assertThat(active).hasSize(1);
        ForexSerieMetadata usd = active.getFirst();
        assertThat(usd.currencyCode()).isEqualTo("USD");
        assertThat(usd.displayNameEn()).isEqualTo("US Dollar");
        assertThat(usd.displayNameTr()).isEqualTo("ABD Doları");
        assertThat(usd.unit()).isEqualTo(1);
        assertThat(usd.hasEfektif()).isTrue();
    }

    @Test
    void should_detectUnit100_when_serieNameContainsHundred() {
        List<EvdsSerieResponse> doviz = List.of(
                new EvdsSerieResponse("TP.DK.JPY.A.YTL",
                        "(JPY) 100 Japon Yeni (Döviz Alış)",
                        "(JPY) 100 Japanese Yen (Buying)",
                        "02-01-1990", TODAY, "GÜNLÜK"));

        List<ForexSerieMetadata> active = resolver.resolveActive(doviz, List.of());

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().unit()).isEqualTo(100);
        assertThat(active.getFirst().hasEfektif()).isFalse();
    }

    @Test
    void should_excludeDeprecatedCurrency_when_endDateOver6MonthsAgo() {
        String oldDate = LocalDate.of(2001, 12, 31).format(EVDS_DATE_FMT);
        List<EvdsSerieResponse> doviz = List.of(
                new EvdsSerieResponse("TP.DK.DEM.A.YTL",
                        "(DEM) Alman Markı (Döviz Alış)",
                        "(DEM) German Mark (Buying)",
                        "02-01-1950", oldDate, "GÜNLÜK"));

        List<ForexSerieMetadata> active = resolver.resolveActive(doviz, List.of());

        assertThat(active).isEmpty();
    }

    @Test
    void should_includeSeriesWithoutEfektif_when_efektifListEmpty() {
        List<EvdsSerieResponse> doviz = List.of(
                new EvdsSerieResponse("TP.DK.BGN.A.YTL",
                        "(BGN) Bulgar Levası (Döviz Alış)",
                        "(BGN) Bulgarian Lev (Buying)",
                        "01-01-2008", TODAY, "GÜNLÜK"));

        List<ForexSerieMetadata> active = resolver.resolveActive(doviz, List.of());

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().hasEfektif()).isFalse();
    }

    @Test
    void should_skipBuyingSuffixOnly_when_dovizListContainsOtherSuffixes() {
        List<EvdsSerieResponse> doviz = List.of(
                new EvdsSerieResponse("TP.DK.USD.S.YTL",
                        "(USD) ABD Doları (Döviz Satış)",
                        "(USD) US Dollar (Selling)",
                        "02-01-1950", TODAY, "GÜNLÜK"),
                new EvdsSerieResponse("TP.DK.USD.A.YTL",
                        "(USD) ABD Doları (Döviz Alış)",
                        "(USD) US Dollar (Buying)",
                        "02-01-1950", TODAY, "GÜNLÜK"));

        List<ForexSerieMetadata> active = resolver.resolveActive(doviz, List.of());

        assertThat(active).hasSize(1);
        assertThat(active.getFirst().currencyCode()).isEqualTo("USD");
    }

    @Test
    void should_returnTrue_when_isActiveCurrencyCodeFound() {
        List<EvdsSerieResponse> doviz = List.of(
                new EvdsSerieResponse("TP.DK.USD.A.YTL",
                        "(USD) ABD Doları (Döviz Alış)",
                        "(USD) US Dollar (Buying)",
                        "02-01-1950", TODAY, "GÜNLÜK"));

        assertThat(resolver.isActiveCurrencyCode(doviz, "USD")).isTrue();
        assertThat(resolver.isActiveCurrencyCode(doviz, "usd")).isTrue();
    }

    @Test
    void should_returnFalse_when_isActiveCurrencyCodeNotFound() {
        List<EvdsSerieResponse> doviz = List.of(
                new EvdsSerieResponse("TP.DK.USD.A.YTL",
                        "(USD) ABD Doları (Döviz Alış)",
                        "(USD) US Dollar (Buying)",
                        "02-01-1950", TODAY, "GÜNLÜK"));

        assertThat(resolver.isActiveCurrencyCode(doviz, "EUR")).isFalse();
        assertThat(resolver.isActiveCurrencyCode(doviz, "")).isFalse();
        assertThat(resolver.isActiveCurrencyCode(doviz, null)).isFalse();
    }
}
