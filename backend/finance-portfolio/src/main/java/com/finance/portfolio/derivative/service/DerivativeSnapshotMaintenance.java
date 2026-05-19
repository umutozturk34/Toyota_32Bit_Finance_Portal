package com.finance.portfolio.derivative.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.service.SnapshotCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DerivativeSnapshotMaintenance {

    private final ViopCandleRepository candleRepository;
    private final HistoricalPricingPort historicalPricingPort;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final SnapshotCalculationService snapshotCalculator;

    public void backfillSnapshots(DerivativePosition position) {
        if (position.getViopContract() == null) return;
        String symbol = position.getViopContract().getSymbol();
        LocalDate from = position.getEntryDate();
        LocalDate to = position.getCloseDate() != null
                ? position.getCloseDate().minusDays(1)
                : LocalDate.now();
        if (from == null || from.isAfter(to)) return;

        Map<LocalDate, BigDecimal> closeByDate = loadCandleCloses(symbol, from, to);
        String currency = position.getViopContract().getCurrency();
        boolean needsFxConversion = currency != null && !"TRY".equalsIgnoreCase(currency);
        Map<LocalDate, BigDecimal> fxByDate = needsFxConversion
                ? historicalPricingPort.getPriceSeries(MarketType.FOREX, currency.toUpperCase(),
                        from.minusDays(7), to)
                : Map.of();

        BigDecimal entryFxAtOpen = needsFxConversion
                ? Optional.ofNullable(closestPriorRate(fxByDate, from)).orElse(BigDecimal.ONE)
                : BigDecimal.ONE;
        BigDecimal lastKnown = needsFxConversion && position.getEntryPrice() != null && entryFxAtOpen.signum() > 0
                ? position.getEntryPrice().divide(entryFxAtOpen, 8, RoundingMode.HALF_UP)
                : position.getEntryPrice();
        BigDecimal lastFxRate = BigDecimal.ONE;
        LocalDate closeDate = position.getCloseDate();
        BigDecimal closePriceOverride = position.getClosePrice();
        List<PortfolioAssetDailySnapshot> batch = new ArrayList<>();
        PortfolioAssetDailySnapshot priorInBatch = null;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean useCloseOverride = closeDate != null && date.equals(closeDate) && closePriceOverride != null;
            BigDecimal close = useCloseOverride
                    ? closePriceOverride
                    : closeByDate.getOrDefault(date, lastKnown);
            if (close == null) continue;
            if (!useCloseOverride) lastKnown = close;
            BigDecimal fxRate;
            if (useCloseOverride || !needsFxConversion) {
                fxRate = BigDecimal.ONE;
            } else {
                BigDecimal byDay = closestPriorRate(fxByDate, date);
                fxRate = byDay != null && byDay.signum() > 0 ? byDay : lastFxRate;
                lastFxRate = fxRate;
            }
            LocalDateTime ts = date.atTime(LocalTime.NOON);
            PortfolioAssetDailySnapshot snapshot = snapshotCalculator
                    .buildDerivativeAssetSnapshotAt(position.getPortfolio().getId(), position, ts, close, fxRate, priorInBatch);
            if (snapshot != null) {
                batch.add(snapshot);
                priorInBatch = snapshot;
            }
        }
        if (!batch.isEmpty()) {
            assetSnapshotRepository.saveAll(batch);
        }
    }

    /**
     * Per-position backfills emit one row per position per date, so multi-position symbols
     * (partial closes that split into a closed slice + reduced original, multiple open lots
     * on the same contract) leave duplicate {@code (portfolio, type, code, created_at)} rows.
     * Merges those duplicates into a single per-asset row.
     */
    public void consolidateSymbolSnapshots(Long portfolioId, String symbol) {
        if (symbol == null) return;
        List<PortfolioAssetDailySnapshot> all = assetSnapshotRepository
                .findByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtBetweenOrderByCreatedAtAsc(
                        portfolioId, AssetType.VIOP, symbol,
                        LocalDateTime.of(1970, 1, 1, 0, 0),
                        LocalDateTime.now().plusYears(1));
        if (all == null || all.isEmpty()) return;
        Map<LocalDateTime, List<PortfolioAssetDailySnapshot>> grouped = new LinkedHashMap<>();
        for (PortfolioAssetDailySnapshot s : all) {
            grouped.computeIfAbsent(s.getCreatedAt(), k -> new ArrayList<>()).add(s);
        }
        List<PortfolioAssetDailySnapshot> toSave = new ArrayList<>();
        List<Long> toDelete = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<PortfolioAssetDailySnapshot>> entry : grouped.entrySet()) {
            List<PortfolioAssetDailySnapshot> bucket = entry.getValue();
            if (bucket.size() <= 1) continue;
            PortfolioAssetDailySnapshot keeper = mergeDuplicates(bucket, toDelete);
            toSave.add(keeper);
        }
        if (!toSave.isEmpty()) assetSnapshotRepository.saveAll(toSave);
        if (!toDelete.isEmpty()) assetSnapshotRepository.deleteAllByIdInBatch(toDelete);
    }

    private Map<LocalDate, BigDecimal> loadCandleCloses(String symbol, LocalDate from, LocalDate to) {
        LocalDateTime fromDT = from.atStartOfDay();
        LocalDateTime toDT = to.plusDays(1).atStartOfDay().minusSeconds(1);
        Map<LocalDate, BigDecimal> closeByDate = new HashMap<>();
        for (ViopCandle candle : candleRepository
                .findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(symbol, fromDT, toDT)) {
            if (candle.getCandleDate() != null && candle.getClose() != null) {
                closeByDate.put(candle.getCandleDate().toLocalDate(), candle.getClose());
            }
        }
        return closeByDate;
    }

    private PortfolioAssetDailySnapshot mergeDuplicates(List<PortfolioAssetDailySnapshot> bucket,
                                                        List<Long> toDelete) {
        PortfolioAssetDailySnapshot keeper = bucket.get(0);
        BigDecimal mv = nullToZero(keeper.getMarketValueTry());
        BigDecimal pnl = nullToZero(keeper.getPnlTry());
        BigDecimal qty = nullToZero(keeper.getQuantity());
        BigDecimal totalCost = nullToZero(keeper.getTotalCostTry());
        BigDecimal unitNum = nullToZero(keeper.getUnitPriceTry()).multiply(nullToZero(keeper.getQuantity()));
        BigDecimal sumDaily = BigDecimal.ZERO;
        boolean dailyPresent = keeper.getDailyPnlTry() != null;
        if (dailyPresent) sumDaily = sumDaily.add(keeper.getDailyPnlTry());
        for (int i = 1; i < bucket.size(); i++) {
            PortfolioAssetDailySnapshot dup = bucket.get(i);
            mv = mv.add(nullToZero(dup.getMarketValueTry()));
            pnl = pnl.add(nullToZero(dup.getPnlTry()));
            qty = qty.add(nullToZero(dup.getQuantity()));
            totalCost = totalCost.add(nullToZero(dup.getTotalCostTry()));
            unitNum = unitNum.add(nullToZero(dup.getUnitPriceTry()).multiply(nullToZero(dup.getQuantity())));
            if (dup.getDailyPnlTry() != null) {
                sumDaily = sumDaily.add(dup.getDailyPnlTry());
                dailyPresent = true;
            }
            toDelete.add(dup.getId());
        }
        keeper.setMarketValueTry(mv);
        keeper.setPnlTry(pnl);
        keeper.setQuantity(qty);
        keeper.setTotalCostTry(totalCost);
        keeper.setUnitPriceTry(qty.signum() > 0
                ? unitNum.divide(qty, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : keeper.getUnitPriceTry());
        BigDecimal dailyPercent = null;
        if (dailyPresent && mv.signum() != 0) {
            BigDecimal prior = mv.subtract(sumDaily);
            if (prior.signum() > 0) {
                dailyPercent = sumDaily.multiply(new BigDecimal("100"))
                        .divide(prior, MoneyScale.PRICE, RoundingMode.HALF_UP);
            }
        }
        keeper.setDailyPnlTry(dailyPresent ? sumDaily : null);
        keeper.setDailyPnlPercent(dailyPercent);
        return keeper;
    }

    static BigDecimal closestPriorRate(Map<LocalDate, BigDecimal> series, LocalDate target) {
        if (series == null || series.isEmpty()) return null;
        LocalDate cursor = target;
        for (int i = 0; i <= 30; i++) {
            BigDecimal rate = series.get(cursor);
            if (rate != null) return rate;
            cursor = cursor.minusDays(1);
        }
        return null;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
