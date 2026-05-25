package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.portfolio.dto.response.CurrencyFramePct;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioPosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Log4j2
@Service
@RequiredArgsConstructor
public class MultiCurrencyPnlCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int RATE_SCALE = 10;
    private static final List<String> TARGETS = List.of("USD", "EUR");
    private static final int LOOKBACK_DAYS = 7;

    private final HistoricalPricingPort historicalPricingPort;

    public Map<String, CurrencyFramePct> compute(
            List<PortfolioPosition> positions,
            BigDecimal totalValueTry,
            BigDecimal dailyPnlTry,
            BigDecimal pnlPercentTry,
            BigDecimal dailyPnlPercentTry) {
        Map<String, CurrencyFramePct> frames = new LinkedHashMap<>();
        BigDecimal totalEntryTry = sumEntryValuesTry(positions);
        BigDecimal totalPnlTry = totalValueTry != null ? totalValueTry.subtract(totalEntryTry) : null;
        frames.put("TRY", new CurrencyFramePct(pnlPercentTry, dailyPnlPercentTry,
                totalValueTry, totalEntryTry, totalPnlTry, dailyPnlTry));
        if (totalValueTry == null) {
            for (String target : TARGETS) frames.put(target, CurrencyFramePct.empty());
            return frames;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate oldestEntry = positions.stream()
                .filter(p -> p.getEntryDate() != null)
                .map(p -> p.getEntryDate().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(today.minusYears(1));

        for (String target : TARGETS) {
            frames.put(target, computeFrame(target, positions, totalValueTry, dailyPnlTry,
                    today, yesterday, oldestEntry));
        }
        return frames;
    }

    private BigDecimal sumEntryValuesTry(List<PortfolioPosition> positions) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            if (pos.getEntryPrice() == null || pos.getQuantity() == null) continue;
            total = total.add(pos.getEntryPrice().multiply(pos.getQuantity()));
        }
        return total;
    }

    private CurrencyFramePct computeFrame(String target, List<PortfolioPosition> positions,
                                          BigDecimal totalValueTry, BigDecimal dailyPnlTry,
                                          LocalDate today, LocalDate yesterday, LocalDate oldestEntry) {
        TreeMap<LocalDate, BigDecimal> fxSeries = loadSeries(target, oldestEntry, today);
        BigDecimal fxToday = closestPrior(fxSeries, today);
        BigDecimal fxYesterday = closestPrior(fxSeries, yesterday);
        if (fxToday == null || fxYesterday == null || fxToday.signum() <= 0 || fxYesterday.signum() <= 0) {
            return CurrencyFramePct.empty();
        }

        BigDecimal todayInTarget = totalValueTry.divide(fxToday, RATE_SCALE, RoundingMode.HALF_UP);

        BigDecimal entryInTarget = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            if (pos.getEntryPrice() == null || pos.getQuantity() == null) continue;
            LocalDate entryDate = pos.getEntryDate() != null
                    ? pos.getEntryDate().toLocalDate() : today;
            BigDecimal fxEntry = closestPrior(fxSeries, entryDate);
            if (fxEntry == null || fxEntry.signum() <= 0) continue;
            BigDecimal posEntryTry = pos.getEntryPrice().multiply(pos.getQuantity());
            entryInTarget = entryInTarget.add(
                    posEntryTry.divide(fxEntry, RATE_SCALE, RoundingMode.HALF_UP));
        }

        BigDecimal pnlInTarget = todayInTarget.subtract(entryInTarget)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnlPct = entryInTarget.signum() > 0
                ? pnlInTarget.multiply(HUNDRED)
                        .divide(entryInTarget, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;

        BigDecimal dailyPct = null;
        BigDecimal dailyPnlInTarget = null;
        if (dailyPnlTry != null && fxToday.compareTo(fxYesterday) != 0) {
            BigDecimal yesterdayTry = totalValueTry.subtract(dailyPnlTry);
            BigDecimal yesterdayInTarget = yesterdayTry.divide(fxYesterday, RATE_SCALE, RoundingMode.HALF_UP);
            dailyPnlInTarget = todayInTarget.subtract(yesterdayInTarget)
                    .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            if (yesterdayInTarget.signum() > 0) {
                dailyPct = dailyPnlInTarget.multiply(HUNDRED)
                        .divide(yesterdayInTarget, MoneyScale.PRICE, RoundingMode.HALF_UP);
            }
        }

        BigDecimal todayInTargetScaled = todayInTarget.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal entryInTargetScaled = entryInTarget.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        return new CurrencyFramePct(pnlPct, dailyPct, todayInTargetScaled,
                entryInTargetScaled, pnlInTarget, dailyPnlInTarget);
    }

    private TreeMap<LocalDate, BigDecimal> loadSeries(String target, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> raw = historicalPricingPort.getPriceSeries(
                MarketType.FOREX, target, from.minusDays(LOOKBACK_DAYS), to.plusDays(1));
        if (raw == null || raw.isEmpty()) return new TreeMap<>();
        return new TreeMap<>(raw);
    }

    private BigDecimal closestPrior(TreeMap<LocalDate, BigDecimal> series, LocalDate date) {
        if (series.isEmpty()) return null;
        Map.Entry<LocalDate, BigDecimal> entry = series.floorEntry(date);
        if (entry != null && entry.getValue() != null && entry.getValue().signum() > 0) {
            return entry.getValue();
        }
        return null;
    }
}
