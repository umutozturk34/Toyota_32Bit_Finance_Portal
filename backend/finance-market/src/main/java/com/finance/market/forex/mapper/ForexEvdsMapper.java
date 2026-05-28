package com.finance.market.forex.mapper;

import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.market.forex.service.ForexSerieMetadata;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps EVDS rate-data rows into {@link ForexCandle}s and locates the latest/earliest dated rows.
 * EVDS keys dots are converted to underscores, zero values are treated as missing, and prices are
 * divided by the currency's quote unit so all rates share a single-unit basis.
 */
@Log4j2
@Component
public class ForexEvdsMapper {

    private static final DateTimeFormatter EVDS_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** Builds candles for all dated rows that carry at least one non-zero price. */
    public List<ForexCandle> toCandles(Forex forex, ForexSerieMetadata metadata, EvdsDataResponse response, int scale) {
        if (response.items() == null || response.items().isEmpty()) return List.of();

        List<ForexCandle> candles = new ArrayList<>(response.items().size());
        for (Map<String, Object> item : response.items()) {
            LocalDateTime candleDate = parseCandleDate(item);
            if (candleDate == null) continue;

            BigDecimal scaledBuying = divideByUnit(
                    extractNonZero(item, dotToUnderscore(metadata.dovizBuyingCode())),
                    metadata.unit(), scale);
            BigDecimal scaledSelling = divideByUnit(
                    extractNonZero(item, dotToUnderscore(metadata.dovizSellingCode())),
                    metadata.unit(), scale);
            BigDecimal scaledEffectiveBuying = metadata.hasEfektif() ? divideByUnit(
                    extractNonZero(item, dotToUnderscore(metadata.efektifBuyingCode())),
                    metadata.unit(), scale) : null;
            BigDecimal scaledEffectiveSelling = metadata.hasEfektif() ? divideByUnit(
                    extractNonZero(item, dotToUnderscore(metadata.efektifSellingCode())),
                    metadata.unit(), scale) : null;

            if (scaledBuying == null && scaledSelling == null
                    && scaledEffectiveBuying == null && scaledEffectiveSelling == null) continue;

            candles.add(ForexCandle.builder()
                    .forex(forex)
                    .currencyCode(forex.getCurrencyCode())
                    .candleDate(candleDate)
                    .sellingPrice(scaledSelling)
                    .buyingPrice(scaledBuying)
                    .effectiveBuyingPrice(scaledEffectiveBuying)
                    .effectiveSellingPrice(scaledEffectiveSelling)
                    .build());
        }
        return candles;
    }

    /** Scans from the end for the most recent row with any price, returning raw (unscaled) values. */
    public ItemRow extractLatestRow(EvdsDataResponse response, ForexSerieMetadata metadata) {
        if (response.items() == null || response.items().isEmpty()) return null;
        for (int i = response.items().size() - 1; i >= 0; i--) {
            Map<String, Object> item = response.items().get(i);
            BigDecimal buying = extractNonZero(item, dotToUnderscore(metadata.dovizBuyingCode()));
            BigDecimal selling = extractNonZero(item, dotToUnderscore(metadata.dovizSellingCode()));
            BigDecimal effectiveBuying = metadata.hasEfektif()
                    ? extractNonZero(item, dotToUnderscore(metadata.efektifBuyingCode()))
                    : null;
            BigDecimal effectiveSelling = metadata.hasEfektif()
                    ? extractNonZero(item, dotToUnderscore(metadata.efektifSellingCode()))
                    : null;
            if (buying == null && selling == null && effectiveBuying == null && effectiveSelling == null) continue;
            return new ItemRow(parseCandleDate(item), buying, selling, effectiveBuying, effectiveSelling);
        }
        return null;
    }

    public LocalDate extractEarliestDate(EvdsDataResponse response) {
        if (response.items() == null || response.items().isEmpty()) return null;
        LocalDateTime dt = parseCandleDate(response.items().getFirst());
        return dt == null ? null : dt.toLocalDate();
    }

    private LocalDateTime parseCandleDate(Map<String, Object> item) {
        Object raw = item.get("Tarih");
        if (raw == null) return null;
        try {
            LocalDate date = LocalDate.parse(raw.toString().trim(), EVDS_DATE_FMT);
            return LocalDateTime.of(date, LocalTime.MIDNIGHT);
        } catch (Exception ex) {
            log.warn("Failed to parse Tarih from EVDS item: '{}'", raw);
            return null;
        }
    }

    /** Parses a numeric EVDS cell, treating absent, blank, unparseable, and zero values as null. */
    private BigDecimal extractNonZero(Map<String, Object> item, String key) {
        Object raw = item.get(key);
        if (raw == null) return null;
        try {
            BigDecimal value;
            if (raw instanceof BigDecimal bd) value = bd;
            else if (raw instanceof Number num) value = new BigDecimal(num.toString());
            else {
                String str = raw.toString().trim();
                if (str.isEmpty()) return null;
                value = new BigDecimal(str);
            }
            return value.signum() == 0 ? null : value;
        } catch (NumberFormatException ex) {
            log.warn("Failed to parse BigDecimal for key='{}', raw='{}'", key, raw);
            return null;
        }
    }

    private BigDecimal divideByUnit(BigDecimal value, int unit, int scale) {
        if (value == null) return null;
        if (unit <= 1) return value.setScale(scale, RoundingMode.HALF_UP);
        return value.divide(BigDecimal.valueOf(unit), scale, RoundingMode.HALF_UP);
    }

    private static String dotToUnderscore(String code) {
        return code.replace('.', '_');
    }

    public record ItemRow(
            LocalDateTime candleDate,
            BigDecimal buyingRaw,
            BigDecimal sellingRaw,
            BigDecimal effectiveBuyingRaw,
            BigDecimal effectiveSellingRaw
    ) {
    }
}
