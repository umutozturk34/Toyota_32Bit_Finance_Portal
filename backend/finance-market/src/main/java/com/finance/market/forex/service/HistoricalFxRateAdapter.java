package com.finance.market.forex.service;

import com.finance.common.model.Currency;
import com.finance.market.core.service.FxRateProvider;
import com.finance.market.forex.config.FxProperties;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.repository.ForexCandleRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Date-accurate {@link FxRateProvider} backed by stored forex candles (reads {@code sellingPrice}).
 * For a past date it uses that date's rate, falling back to the closest PRIOR available day
 * ({@code headMap} ≤ target, {@code lastKey}) bounded by {@link FxProperties#getLookbackDays()};
 * it never borrows a future date or today's spot for a past date. Cross-rates pivot through TRY
 * (and X/TRY is the inverse of the TRY/X series). Per-pair series are Caffeine-cached.
 */
@Log4j2
@Component
public class HistoricalFxRateAdapter implements FxRateProvider {

    private static final int RATE_SCALE = 10;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final ForexCandleRepository forexCandleRepository;
    private final FxProperties fxProperties;
    private final Cache<CurrencyPair, SortedMap<LocalDate, BigDecimal>> seriesCache;

    public HistoricalFxRateAdapter(ForexCandleRepository forexCandleRepository,
                                   FxProperties fxProperties) {
        this.forexCandleRepository = forexCandleRepository;
        this.fxProperties = fxProperties;
        this.seriesCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(fxProperties.getCacheTtlMinutes()))
                .maximumSize(fxProperties.getCacheMaxEntries())
                .build();
    }

    @Override
    public Optional<BigDecimal> rateAt(Currency from, Currency to, LocalDate date) {
        if (from == null || to == null || date == null) {
            return Optional.empty();
        }
        if (from == to) {
            return Optional.of(ONE);
        }
        SortedMap<LocalDate, BigDecimal> series = pairSeries(from, to);
        if (series.isEmpty()) {
            return Optional.empty();
        }
        return closestPriorRate(series, date);
    }

    /** Returns the rate series clipped to {@code [fromDate, toDate]} inclusive; identity pairs yield 1 per day. */
    @Override
    public SortedMap<LocalDate, BigDecimal> seriesAt(Currency from, Currency to,
                                                     LocalDate fromDate, LocalDate toDate) {
        if (from == null || to == null || fromDate == null || toDate == null) {
            return new TreeMap<>();
        }
        if (from == to) {
            SortedMap<LocalDate, BigDecimal> ones = new TreeMap<>();
            LocalDate cursor = fromDate;
            while (!cursor.isAfter(toDate)) {
                ones.put(cursor, ONE);
                cursor = cursor.plusDays(1);
            }
            return ones;
        }
        SortedMap<LocalDate, BigDecimal> full = pairSeries(from, to);
        return new TreeMap<>(full.subMap(fromDate, toDate.plusDays(1)));
    }

    private SortedMap<LocalDate, BigDecimal> pairSeries(Currency from, Currency to) {
        CurrencyPair key = new CurrencyPair(from, to);
        SortedMap<LocalDate, BigDecimal> cached = seriesCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        SortedMap<LocalDate, BigDecimal> loaded = loadSeries(from, to);
        seriesCache.put(key, loaded);
        return loaded;
    }

    /**
     * Builds the {@code from->to} series: X/TRY is read directly, TRY/X is the inverse of X/TRY,
     * and a non-TRY pair is composed by crossing both legs through TRY.
     */
    private SortedMap<LocalDate, BigDecimal> loadSeries(Currency from, Currency to) {
        if (from == Currency.TRY) {
            SortedMap<LocalDate, BigDecimal> reverse = loadSeries(to, Currency.TRY);
            return invertSeries(reverse);
        }
        if (to == Currency.TRY) {
            return loadDirectToTry(from);
        }
        SortedMap<LocalDate, BigDecimal> fromTry = loadSeries(from, Currency.TRY);
        SortedMap<LocalDate, BigDecimal> toTry = loadSeries(to, Currency.TRY);
        return crossViaTry(fromTry, toTry);
    }

    private SortedMap<LocalDate, BigDecimal> loadDirectToTry(Currency from) {
        SortedMap<LocalDate, BigDecimal> out = new TreeMap<>();
        for (ForexCandle candle : forexCandleRepository
                .findByCurrencyCodeOrderByCandleDateAsc(from.name())) {
            if (candle.getSellingPrice() == null || candle.getCandleDate() == null) {
                continue;
            }
            out.put(candle.getCandleDate().toLocalDate(), candle.getSellingPrice());
        }
        return out;
    }

    private SortedMap<LocalDate, BigDecimal> invertSeries(SortedMap<LocalDate, BigDecimal> src) {
        SortedMap<LocalDate, BigDecimal> out = new TreeMap<>();
        for (var entry : src.entrySet()) {
            BigDecimal v = entry.getValue();
            if (v == null || v.signum() <= 0) continue;
            out.put(entry.getKey(), ONE.divide(v, RATE_SCALE, RoundingMode.HALF_UP));
        }
        return out;
    }

    private SortedMap<LocalDate, BigDecimal> crossViaTry(SortedMap<LocalDate, BigDecimal> fromTry,
                                                         SortedMap<LocalDate, BigDecimal> toTry) {
        SortedMap<LocalDate, BigDecimal> out = new TreeMap<>();
        // Key the cross over the UNION of both legs' candle dates and resolve EACH leg independently via its
        // own closest-prior rate. Iterating only the from-leg's keys (the previous behaviour) dropped every
        // date where only the to-leg moved — the two TRY legs are ingested separately so their calendars
        // diverge — freezing the cross at a stale to-leg rate (e.g. a missed EUR/TRY jump → wrong USD-in-EUR
        // value). Each leg must use its own closest-prior at the date, then divide.
        java.util.TreeSet<LocalDate> dates = new java.util.TreeSet<>(fromTry.keySet());
        dates.addAll(toTry.keySet());
        for (LocalDate date : dates) {
            BigDecimal fromRate = closestPriorRate(fromTry, date).orElse(null);
            BigDecimal toRate = closestPriorRate(toTry, date).orElse(null);
            if (fromRate == null || toRate == null || toRate.signum() <= 0) continue;
            out.put(date, fromRate.divide(toRate, RATE_SCALE, RoundingMode.HALF_UP));
        }
        return out;
    }

    /**
     * Picks the rate on {@code target} or the nearest earlier date, returning empty when the gap
     * exceeds the configured lookback so stale rates are not silently applied.
     */
    private Optional<BigDecimal> closestPriorRate(SortedMap<LocalDate, BigDecimal> series,
                                                  LocalDate target) {
        SortedMap<LocalDate, BigDecimal> head = series.headMap(target.plusDays(1));
        if (head.isEmpty()) {
            return Optional.empty();
        }
        LocalDate last = head.lastKey();
        long gap = java.time.temporal.ChronoUnit.DAYS.between(last, target);
        if (gap > fxProperties.getLookbackDays()) {
            return Optional.empty();
        }
        return Optional.ofNullable(head.get(last));
    }

    private record CurrencyPair(Currency from, Currency to) {}
}
