package com.finance.portfolio.service.summary;

import com.finance.common.model.MarketType;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.service.AssetPricingPort;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.util.EnumParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes the CURRENT-VALUE portfolio allocation breakdown in TRY for the pie/donut charts: grouping by asset code
 * or type, optional filtering (asset type, kind FUTURE/OPTION, or the synthetic CASH bucket of closed-position
 * proceeds), and a top-N cap that folds the tail into an OTHER bucket. Open spot uses live prices; open derivatives
 * use live-to-TRY notional+PnL. The {@code realizedPnl} mode is a distinct closed-positions-only computation,
 * delegated to {@link RealizedPnlAllocationCalculator}; the entry point and cap stay here so both share one surface.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class AllocationCalculator {

    private static final String CASH_KEY = "CASH";
    private static final String REALIZED_PNL_MODE = "realizedPnl";
    private static final String GROUP_BY_TYPE_MODE = "assetType";
    private static final String FUTURE_FILTER = "FUTURE";
    private static final String OPTION_FILTER = "OPTION";
    private static final String OTHER_KEY = "OTHER";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioResponseMapper responseMapper;
    private final ViopCandleRepository viopCandleRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final CurrencyFrameConverter frameConverter;
    private final AllocationFxFrameLoader fxFrameLoader;
    private final RealizedPnlAllocationCalculator realizedPnlCalculator;

    /** Entry point: builds the allocation (by mode/filter) and applies the optional top-N cap. */
    List<AllocationItem> compute(Long portfolioId, String mode, String assetTypeFilter, Integer limit) {
        log.debug("Computing allocation portfolioId={} mode={} filter={} limit={}",
                portfolioId, mode, assetTypeFilter, limit);
        List<AllocationItem> full = computeAllocation(portfolioId, mode, assetTypeFilter);
        return applyLimit(full, limit);
    }

    private List<AllocationItem> computeAllocation(Long portfolioId, String mode, String assetTypeFilter) {
        if (REALIZED_PNL_MODE.equals(mode)) {
            return realizedPnlCalculator.build(portfolioId, assetTypeFilter);
        }
        AllocationContext ctx = AllocationContext.from(mode, assetTypeFilter);
        // The CASH bucket's cost/realized breakdown is rendered per-currency at each closed position's
        // entry/exit-date FX, and open derivatives carry per-date EQUITY frames so their donut slice
        // matches the K/Z card. Load the frame series whenever either consumes it (cash bucket emitted,
        // OR derivatives are in scope under an asset-type/kind filter). For pure non-derivative spot
        // buckets it stays unused, so skip the FX fetch.
        Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries =
                ctx.shouldEmitCashBucket() || ctx.includeDerivatives()
                        ? fxFrameLoader.loadFxFrameSeries(portfolioId) : Map.of();
        AllocationAcc acc = new AllocationAcc();
        List<PortfolioPosition> positions = loadSpotPositions(portfolioId, ctx);
        addSpotPositions(positions, ctx, acc, fxSeries);
        if (ctx.includeDerivatives()) {
            addDerivativePositions(portfolioId, ctx, acc, fxSeries);
        }
        if (ctx.shouldEmitCashBucket()) acc.appendCashBucket();
        return toAllocationItems(acc);
    }

    private List<PortfolioPosition> loadSpotPositions(Long portfolioId, AllocationContext ctx) {
        List<PortfolioPosition> all = positionRepository.findByPortfolioId(portfolioId);
        return ctx.cashOnly() ? all : filterByType(all, ctx.assetTypeFilter());
    }

    private void addSpotPositions(List<PortfolioPosition> positions, AllocationContext ctx, AllocationAcc acc,
                                  Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        List<PortfolioPosition> openPositions = positions.stream().filter(p -> !p.isClosed()).toList();
        Map<AssetKey, BigDecimal> prices = ctx.cashOnly()
                ? Map.of() : pricingPort.getPricesTry(toKeys(openPositions));
        for (PortfolioPosition pos : positions) {
            if (pos.isClosed()) addClosedSpot(pos, ctx, acc, fxSeries);
            else if (!ctx.cashOnly()) addOpenSpot(pos, prices.get(pos.toAssetKey()), ctx, acc);
        }
    }

    private void addClosedSpot(PortfolioPosition pos, AllocationContext ctx, AllocationAcc acc,
                               Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        if (pos.getExitPrice() == null) return;
        BigDecimal exitValue = pos.getExitPrice().multiply(pos.getQuantity())
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        if (ctx.splitClosedByAsset()) {
            acc.addBucket(pos.getAssetCode(), pos.getAssetType().name(), exitValue);
        } else {
            LocalDate entryDate = pos.getEntryDate() != null ? pos.getEntryDate().toLocalDate() : null;
            LocalDate exitDate = pos.getExitDate() != null ? pos.getExitDate().toLocalDate() : null;
            acc.addCash(exitValue, pos.entryValue(), pos.realizedPnl(),
                    frameConverter.convertToFrames(pos.entryValue(), entryDate, fxSeries),
                    frameConverter.realizedFrames(exitValue, exitDate, pos.entryValue(), entryDate, fxSeries));
        }
    }

    private void addOpenSpot(PortfolioPosition pos, BigDecimal price, AllocationContext ctx, AllocationAcc acc) {
        // Mirror PortfolioSummaryService.aggregateSpotTotals fallback chain so card total == pie sum:
        // live price → last persisted snapshot price → lot's entry value (asset treated as flat since
        // purchase when no price resolves). Without this, a stale Redis cache for one fund/stock
        // silently dropped that lot's contribution to zero in the pie while the card still summed it,
        // producing the visible "card 4.74M vs pie 4.66M" 80K gap.
        BigDecimal effectivePrice = price != null ? price : lastSnapshotPrice(pos);
        BigDecimal marketValue;
        if (effectivePrice != null) {
            marketValue = effectivePrice.multiply(pos.getQuantity()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        } else {
            marketValue = pos.entryValue().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        }
        String key = ctx.byType() ? pos.getAssetType().name() : pos.getAssetCode();
        acc.addBucket(key, pos.getAssetType().name(), marketValue);
    }

    private BigDecimal lastSnapshotPrice(PortfolioPosition pos) {
        if (pos.getPortfolio() == null || pos.getAssetType() == null || pos.getAssetCode() == null) return null;
        return assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        pos.getPortfolio().getId(), pos.getAssetType(), pos.getAssetCode(),
                        LocalDateTime.now().plusDays(1))
                .map(PortfolioAssetDailySnapshot::getUnitPriceTry)
                .orElse(null);
    }

    private void addDerivativePositions(Long portfolioId, AllocationContext ctx, AllocationAcc acc,
                                        Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        for (DerivativePosition dpos : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (dpos.getViopContract() == null) continue;
            if (!ctx.matchesDerivativeKind(dpos.getViopContract().getKind().name())) continue;
            BigDecimal entryNotional = dpos.nominalExposure();
            if (entryNotional == null) continue;
            if (!dpos.isOpen()) addClosedDerivative(dpos, entryNotional, ctx, acc, fxSeries);
            else if (!ctx.cashOnly()) addOpenDerivative(dpos, entryNotional, ctx, acc, fxSeries);
        }
    }

    private void addClosedDerivative(DerivativePosition dpos, BigDecimal entryNotional,
                                     AllocationContext ctx, AllocationAcc acc,
                                     Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
        BigDecimal exitValue = (realized != null ? entryNotional.add(realized) : entryNotional)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        if (ctx.splitClosedByAsset()) {
            acc.addBucket(dpos.getViopContract().getSymbol(), AssetType.VIOP.name(), exitValue);
        } else {
            // Per-currency realized must be direction-aware: proceeds@closeFX − cost@entryFX reads a profiting
            // SHORT as a loss in USD/EUR (FX drift on the whole notional swamps the realized). See
            // directionalRealizedFrames. TRY-level `realized` is already direction-aware via realizedOrUnrealizedPnl.
            BigDecimal closeNotional = dpos.notionalAt(dpos.getClosePrice());
            int sign = dpos.getDirection() == com.finance.portfolio.derivative.model.DerivativeDirection.SHORT ? -1 : 1;
            Map<String, BigDecimal> realizedByCcy = closeNotional != null
                    ? frameConverter.directionalRealizedFrames(closeNotional, dpos.getCloseDate(), entryNotional, dpos.getEntryDate(), sign, fxSeries)
                    : frameConverter.realizedFrames(exitValue, dpos.getCloseDate(), entryNotional, dpos.getEntryDate(), fxSeries);
            acc.addCash(exitValue, entryNotional, realized,
                    frameConverter.convertToFrames(entryNotional, dpos.getEntryDate(), fxSeries),
                    realizedByCcy);
        }
    }

    private void addOpenDerivative(DerivativePosition dpos, BigDecimal entryNotional,
                                   AllocationContext ctx, AllocationAcc acc,
                                   Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        // Live source mirrors PortfolioSummaryService.openAndClosedDerivativeTotals exactly:
        // contract.lastPrice first, candle-close as fallback. Without the candle fallback the
        // pie under-counted whenever the live cache had no lastPrice for a symbol (real today
        // for our portfolio — produced the visible 80K card-vs-pie gap).
        BigDecimal contractLast = dpos.getViopContract().getLastPrice();
        BigDecimal liveSource = contractLast != null
                ? contractLast : latestCandleClose(dpos.getViopContract().getSymbol());
        BigDecimal currentPriceTry = convertLiveToTry(
                liveSource, dpos.getViopContract().resolvePriceCurrency());
        // Value = EQUITY = entry notional + direction-aware unrealized PnL, NOT the raw current notional. A
        // profiting SHORT's notional falls below cost; its equity rises (cost + profit), matching the headline
        // "Piyasa Değeri" and the K/Z card. value − cost ≡ PnL keeps the slice consistent with every surface.
        // For a LONG/spot, equity == notional, so this only changes the short. Falls back to entry notional
        // when no live/candle price resolves a PnL.
        BigDecimal openPnl = dpos.realizedOrUnrealizedPnl(currentPriceTry);
        BigDecimal marketValue = (openPnl != null
                ? entryNotional.abs().add(openPnl)
                : entryNotional.abs()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        String key = ctx.byType() ? AssetType.VIOP.name() : dpos.getViopContract().getSymbol();
        // Per-currency EQUITY = cost@entry-FX + direction-aware PnL@per-date, attached to the bucket so the
        // donut value matches the K/Z card exactly. Converting the TRY equity at today's spot (the default for
        // a bucket with no frames) drops the entry-date FX on the cost leg, reading €3914 as €3882 etc. PnL =
        // sign × (current notional@today's FX − cost@entry-FX); reuse directionalRealizedFrames with today as the
        // value date. LONG/spot equity == notional so this only shifts a SHORT's foreign value.
        BigDecimal currentNotionalTry = dpos.notionalAt(currentPriceTry);
        int sign = dpos.getDirection() == com.finance.portfolio.derivative.model.DerivativeDirection.SHORT ? -1 : 1;
        Map<String, BigDecimal> costByCcy = frameConverter.convertToFrames(entryNotional.abs(), dpos.getEntryDate(), fxSeries);
        Map<String, BigDecimal> pnlByCcy = currentNotionalTry != null
                ? frameConverter.directionalRealizedFrames(currentNotionalTry, LocalDate.now(), entryNotional.abs(),
                        dpos.getEntryDate(), sign, fxSeries)
                : Map.of();
        acc.addBucketFrames(key, AssetType.VIOP.name(), marketValue, entryNotional.abs(), openPnl, costByCcy, pnlByCcy);
    }

    private BigDecimal latestCandleClose(String symbol) {
        if (symbol == null) return null;
        return viopCandleRepository.findFirstBySymbolAndCloseGreaterThanOrderByCandleDateDesc(symbol, BigDecimal.ZERO)
                .map(ViopCandle::getClose)
                .orElse(null);
    }

    private List<AllocationItem> toAllocationItems(AllocationAcc acc) {
        BigDecimal denominator = acc.totalValue;
        BigDecimal finalCashCost = acc.cashCost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal finalCashRealized = acc.cashRealized.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        return acc.buckets.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    boolean isCash = CASH_KEY.equals(e.getKey());
                    BigDecimal cost = isCash ? finalCashCost : acc.bucketCost.get(e.getKey());
                    BigDecimal realized = isCash ? finalCashRealized : acc.bucketRealized.get(e.getKey());
                    Map<String, BigDecimal> realizedByCcy = isCash ? acc.cashRealizedByCurrency
                            : acc.bucketRealizedByCurrency.getOrDefault(e.getKey(), Map.of());
                    Map<String, BigDecimal> costByCcy = isCash ? acc.cashCostByCurrency
                            : acc.bucketCostByCurrency.getOrDefault(e.getKey(), Map.of());
                    return responseMapper.toAllocationItem(
                            e.getKey(),
                            acc.bucketTypes.get(e.getKey()),
                            e.getValue().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                            percentOf(e.getValue(), denominator),
                            cost != null ? cost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP) : null,
                            realized != null ? realized.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP) : null,
                            Map.copyOf(realizedByCcy), Map.copyOf(costByCcy));
                })
                .toList();
    }

    private static BigDecimal percentOf(BigDecimal value, BigDecimal denominator) {
        return denominator.compareTo(BigDecimal.ZERO) > 0
                ? value.multiply(HUNDRED).divide(denominator, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private List<AllocationItem> applyLimit(List<AllocationItem> items, Integer limit) {
        if (limit == null || limit <= 0 || items.size() <= limit) return items;
        List<AllocationItem> top = items.subList(0, limit - 1);
        List<AllocationItem> rest = items.subList(limit - 1, items.size());
        BigDecimal restValue = rest.stream().map(AllocationItem::valueTry).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restPercent = rest.stream().map(AllocationItem::percent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restCost = rest.stream().map(AllocationItem::costTry).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restRealized = rest.stream().map(AllocationItem::realizedPnlTry).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        AllocationItem other = responseMapper.toAllocationItem(
                OTHER_KEY, OTHER_KEY,
                restValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                restPercent.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                restCost.signum() != 0 ? restCost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP) : null,
                restRealized.signum() != 0 ? restRealized.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP) : null);
        List<AllocationItem> result = new ArrayList<>(top.size() + 1);
        result.addAll(top);
        result.add(other);
        return result;
    }

    private BigDecimal convertLiveToTry(BigDecimal nativePrice, String currency) {
        if (nativePrice == null) return null;
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) return nativePrice;
        // SELLING rate (getPriceTry on FOREX) — same FX field as the card's open-VIOP valuation, so
        // the pie sum stays equal to the card total after the card moved to the selling basis.
        BigDecimal rate = pricingPort.getPriceTry(MarketType.FOREX, currency.toUpperCase());
        if (rate == null || rate.signum() <= 0) {
            // FX cache miss for a non-TRY contract. Null lets addOpenDerivative fall back to
            // entryNotional.abs() (same defensive path as PortfolioSummaryService). Returning the
            // native value as TRY here would shrink a USD pie slice by ~30x.
            log.warn("Live FX missing currency={} — allocation slice will fall back to entry notional", currency);
            return null;
        }
        return nativePrice.multiply(rate);
    }

    private List<PortfolioPosition> filterByType(List<PortfolioPosition> positions, String assetTypeName) {
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetTypeName, "enum.field.assetType");
        if (filterType == null) return positions;
        return positions.stream().filter(p -> p.getAssetType() == filterType).toList();
    }

    private List<AssetKey> toKeys(List<PortfolioPosition> positions) {
        return positions.stream().map(PortfolioPosition::toAssetKey).distinct().toList();
    }

    /** Resolved request flags driving which positions and buckets the allocation includes. */
    private record AllocationContext(
            String assetTypeFilter,
            boolean byType,
            boolean cashOnly,
            boolean splitClosedByAsset) {

        static AllocationContext from(String mode, String filter) {
            boolean cashOnly = CASH_KEY.equalsIgnoreCase(filter);
            boolean byType = GROUP_BY_TYPE_MODE.equals(mode);
            return new AllocationContext(filter, byType, cashOnly, cashOnly && !byType);
        }

        boolean includeDerivatives() {
            if (assetTypeFilter == null || assetTypeFilter.isBlank() || cashOnly) return true;
            return AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter)
                    || FUTURE_FILTER.equalsIgnoreCase(assetTypeFilter)
                    || OPTION_FILTER.equalsIgnoreCase(assetTypeFilter);
        }

        boolean matchesDerivativeKind(String kindName) {
            boolean kindFilter = FUTURE_FILTER.equalsIgnoreCase(assetTypeFilter)
                    || OPTION_FILTER.equalsIgnoreCase(assetTypeFilter);
            return !kindFilter || assetTypeFilter.equalsIgnoreCase(kindName);
        }

        boolean shouldEmitCashBucket() {
            return assetTypeFilter == null || assetTypeFilter.isBlank() || cashOnly;
        }
    }

    /** Mutable accumulator of bucket values/types plus the separate cash (closed-proceeds) tallies and running total. */
    private static final class AllocationAcc {
        final Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        final Map<String, String> bucketTypes = new LinkedHashMap<>();
        final Map<String, BigDecimal> cashCostByCurrency = new LinkedHashMap<>();
        final Map<String, BigDecimal> cashRealizedByCurrency = new LinkedHashMap<>();
        // Per-bucket (non-cash) cost/PnL frames — currently populated for open VIOP so its donut value is the
        // per-date EQUITY (cost@entry-FX + direction-aware PnL), matching the K/Z card instead of TRY ÷ today's FX.
        final Map<String, BigDecimal> bucketCost = new LinkedHashMap<>();
        final Map<String, BigDecimal> bucketRealized = new LinkedHashMap<>();
        final Map<String, Map<String, BigDecimal>> bucketCostByCurrency = new LinkedHashMap<>();
        final Map<String, Map<String, BigDecimal>> bucketRealizedByCurrency = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal cashTotal = BigDecimal.ZERO;
        BigDecimal cashCost = BigDecimal.ZERO;
        BigDecimal cashRealized = BigDecimal.ZERO;

        void addBucket(String key, String type, BigDecimal value) {
            buckets.merge(key, value, BigDecimal::add);
            bucketTypes.putIfAbsent(key, type);
            totalValue = totalValue.add(value);
        }

        /** Adds a bucket plus its per-date cost/PnL frames (TRY scalars + per-currency maps), so the slice value
         *  can be shown as the direction-aware equity {@code cost + PnL} in USD/EUR rather than a spot conversion. */
        void addBucketFrames(String key, String type, BigDecimal value, BigDecimal costTry, BigDecimal pnlTry,
                             Map<String, BigDecimal> costByCcy, Map<String, BigDecimal> pnlByCcy) {
            addBucket(key, type, value);
            if (costTry != null) bucketCost.merge(key, costTry, BigDecimal::add);
            if (pnlTry != null) bucketRealized.merge(key, pnlTry, BigDecimal::add);
            mergeFrames(bucketCostByCurrency.computeIfAbsent(key, k -> new LinkedHashMap<>()), costByCcy);
            mergeFrames(bucketRealizedByCurrency.computeIfAbsent(key, k -> new LinkedHashMap<>()), pnlByCcy);
        }

        // CASH bucket cost is converted at each closed position's ENTRY-date FX and realized at its
        // EXIT/CLOSE-date FX (the same per-date basis the realized-PnL view uses), so the donut tooltip
        // shows cost and realized on matching FX dates instead of mixing per-date realized with today's spot.
        void addCash(BigDecimal exitValue, BigDecimal entryValue, BigDecimal realized,
                     Map<String, BigDecimal> costFrames, Map<String, BigDecimal> realizedFrames) {
            cashTotal = cashTotal.add(exitValue);
            if (entryValue != null) cashCost = cashCost.add(entryValue);
            if (realized != null) cashRealized = cashRealized.add(realized);
            mergeFrames(cashCostByCurrency, costFrames);
            mergeFrames(cashRealizedByCurrency, realizedFrames);
        }

        private static void mergeFrames(Map<String, BigDecimal> target, Map<String, BigDecimal> increment) {
            if (increment == null) return;
            for (var e : increment.entrySet()) target.merge(e.getKey(), e.getValue(), BigDecimal::add);
        }

        void appendCashBucket() {
            if (cashTotal.signum() == 0) return;
            buckets.merge(CASH_KEY, cashTotal, BigDecimal::add);
            bucketTypes.putIfAbsent(CASH_KEY, CASH_KEY);
            totalValue = totalValue.add(cashTotal);
        }
    }
}
