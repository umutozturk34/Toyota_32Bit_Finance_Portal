package com.finance.market.core.service;

import com.finance.common.model.Currency;
import com.finance.shared.model.value.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

@Log4j2
@Service
@RequiredArgsConstructor
public class CurrencyConverter {

    private static final int OUTPUT_SCALE = 4;

    private final FxRateProvider fxRateProvider;

    public BigDecimal convertAtDate(BigDecimal amount, Currency from, Currency to, LocalDate date) {
        if (amount == null) {
            return null;
        }
        if (from == to) {
            return amount;
        }
        Optional<BigDecimal> rate = fxRateProvider.rateAt(from, to, date);
        if (rate.isEmpty()) {
            throw new FxRateUnavailableException(from, to, date);
        }
        return amount.multiply(rate.get()).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    public SortedMap<LocalDate, BigDecimal> convertSeries(Map<LocalDate, BigDecimal> series,
                                                          Currency from, Currency to) {
        SortedMap<LocalDate, BigDecimal> out = new TreeMap<>();
        if (series == null || series.isEmpty()) {
            return out;
        }
        if (from == to) {
            out.putAll(series);
            return out;
        }
        for (var entry : series.entrySet()) {
            LocalDate date = entry.getKey();
            BigDecimal value = entry.getValue();
            if (date == null || value == null) continue;
            Optional<BigDecimal> rate = fxRateProvider.rateAt(from, to, date);
            if (rate.isEmpty()) {
                log.debug("Skipping {} -> {} conversion on {} (no rate)", from, to, date);
                continue;
            }
            out.put(date, value.multiply(rate.get()).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP));
        }
        return out;
    }

    public Money convertMoney(Money source, Currency target, LocalDate date) {
        if (source == null) {
            return null;
        }
        if (source.currency() == target) {
            return source;
        }
        Optional<BigDecimal> rate = fxRateProvider.rateAt(source.currency(), target, date);
        if (rate.isEmpty()) {
            throw new FxRateUnavailableException(source.currency(), target, date);
        }
        return source.inCurrency(target, rate.get());
    }
}
