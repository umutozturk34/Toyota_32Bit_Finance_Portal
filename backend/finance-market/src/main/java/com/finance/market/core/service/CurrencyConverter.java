package com.finance.market.core.service;

import com.finance.common.model.Currency;
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

/**
 * Converts amounts between currencies using date-accurate FX rates from {@link FxRateProvider}.
 * Same-currency conversions are pass-through; results are rounded to a fixed output scale.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CurrencyConverter {

    // 8 dp, not 4: a 4-dp money scale quantized small converted prices to zero — a sub-cent fund NAV
    // (e.g. 0.0018 TRY → ~0.0000468 USD) rounded to 0.0000, so the USD/EUR return series read -100%.
    private static final int OUTPUT_SCALE = 8;

    private final FxRateProvider fxRateProvider;

    /**
     * Converts a single amount at the given date.
     *
     * @throws FxRateUnavailableException when no rate exists for the pair on/before that date
     */
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

    /**
     * Per-unit {@code from→to} FX factor at a date, at the provider's full rate precision and with no
     * money-scale rounding. Use this for ratio/chained math — e.g. expressing a native-currency price
     * series in a target currency day by day — where rounding the factor to the 4-dp money scale (as
     * {@link #convertAtDate} does) would quantize a small inverse rate (TRY→USD ≈ 0.026) down to ~3
     * significant figures and inject ~0.2% drift that does not cancel across dates. {@link #convertAtDate}
     * remains the right call when presenting an actual converted amount.
     *
     * @throws FxRateUnavailableException when no rate exists for the pair on/before that date
     */
    public BigDecimal rateAt(Currency from, Currency to, LocalDate date) {
        if (from == to) {
            return BigDecimal.ONE;
        }
        return fxRateProvider.rateAt(from, to, date)
                .orElseThrow(() -> new FxRateUnavailableException(from, to, date));
    }

    /**
     * Converts each dated value at its own date; dates with no available rate are skipped (logged)
     * rather than failing the whole series.
     */
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
}
