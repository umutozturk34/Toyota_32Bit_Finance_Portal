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
        for (var entry : fromTry.entrySet()) {
            BigDecimal toRate = toTry.get(entry.getKey());
            if (toRate == null || toRate.signum() <= 0) {
                toRate = closestPriorRate(toTry, entry.getKey()).orElse(null);
                if (toRate == null || toRate.signum() <= 0) continue;
            }
            out.put(entry.getKey(), entry.getValue().divide(toRate, RATE_SCALE, RoundingMode.HALF_UP));
        }
        return out;
    }

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
