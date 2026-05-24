package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioPosition;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    private static final List<String> FRAME_CURRENCIES = List.of("USD", "EUR");

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioResponseMapper responseMapper;
    private final HistoricalPricingPort historicalPricingPort;

    List<AllocationItem> compute(Long portfolioId, String mode, String assetTypeFilter, Integer limit) {
        log.debug("Computing allocation portfolioId={} mode={} filter={} limit={}",
                portfolioId, mode, assetTypeFilter, limit);
        List<AllocationItem> full = computeAllocation(portfolioId, mode, assetTypeFilter);
        return applyLimit(full, limit);
    }

    private List<AllocationItem> computeAllocation(Long portfolioId, String mode, String assetTypeFilter) {
        if (REALIZED_PNL_MODE.equals(mode)) {
            return buildRealizedPnlAllocation(portfolioId, assetTypeFilter);
        }
        AllocationContext ctx = AllocationContext.from(mode, assetTypeFilter);
        AllocationAcc acc = new AllocationAcc();
        List<PortfolioPosition> positions = loadSpotPositions(portfolioId, ctx);
        addSpotPositions(positions, ctx, acc);
        if (ctx.includeDerivatives()) {
            addDerivativePositions(portfolioId, ctx, acc);
        }
        if (ctx.shouldEmitCashBucket()) acc.appendCashBucket();
        return toAllocationItems(acc);
    }

    private List<PortfolioPosition> loadSpotPositions(Long portfolioId, AllocationContext ctx) {
        List<PortfolioPosition> all = positionRepository.findByPortfolioId(portfolioId);
        return ctx.cashOnly() ? all : filterByType(all, ctx.assetTypeFilter());
    }

    private void addSpotPositions(List<PortfolioPosition> positions, AllocationContext ctx, AllocationAcc acc) {
        List<PortfolioPosition> openPositions = positions.stream().filter(p -> !p.isClosed()).toList();
        Map<AssetKey, BigDecimal> prices = ctx.cashOnly()
                ? Map.of() : pricingPort.getExitPricesTry(toKeys(openPositions));
        for (PortfolioPosition pos : positions) {
            if (pos.isClosed()) addClosedSpot(pos, ctx, acc);
            else if (!ctx.cashOnly()) addOpenSpot(pos, prices.get(pos.toAssetKey()), ctx, acc);
        }
    }

    private void addClosedSpot(PortfolioPosition pos, AllocationContext ctx, AllocationAcc acc) {
        if (pos.getExitPrice() == null) return;
        BigDecimal exitValue = pos.getExitPrice().multiply(pos.getQuantity())
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        if (ctx.splitClosedByAsset()) {
            acc.addBucket(pos.getAssetCode(), pos.getAssetType().name(), exitValue);
        } else {
            acc.addCash(exitValue, pos.entryValue(), pos.realizedPnl());
        }
    }

    private void addOpenSpot(PortfolioPosition pos, BigDecimal price, AllocationContext ctx, AllocationAcc acc) {
        BigDecimal marketValue = price != null
                ? price.multiply(pos.getQuantity()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String key = ctx.byType() ? pos.getAssetType().name() : pos.getAssetCode();
        acc.addBucket(key, pos.getAssetType().name(), marketValue);
    }

    private void addDerivativePositions(Long portfolioId, AllocationContext ctx, AllocationAcc acc) {
        for (DerivativePosition dpos : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (dpos.getViopContract() == null) continue;
            if (!ctx.matchesDerivativeKind(dpos.getViopContract().getKind().name())) continue;
            BigDecimal entryNotional = dpos.nominalExposure();
            if (entryNotional == null) continue;
            if (!dpos.isOpen()) addClosedDerivative(dpos, entryNotional, ctx, acc);
            else if (!ctx.cashOnly()) addOpenDerivative(dpos, entryNotional, ctx, acc);
        }
    }

    private void addClosedDerivative(DerivativePosition dpos, BigDecimal entryNotional,
                                     AllocationContext ctx, AllocationAcc acc) {
        BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
        BigDecimal exitValue = (realized != null ? entryNotional.add(realized) : entryNotional)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        if (ctx.splitClosedByAsset()) {
            acc.addBucket(dpos.getViopContract().getSymbol(), AssetType.VIOP.name(), exitValue);
        } else {
            acc.addCash(exitValue, entryNotional, realized);
        }
    }

    private void addOpenDerivative(DerivativePosition dpos, BigDecimal entryNotional,
                                   AllocationContext ctx, AllocationAcc acc) {
        BigDecimal currentPriceTry = convertLiveToTry(
                dpos.getViopContract().getLastPrice(), dpos.getViopContract().getCurrency());
        BigDecimal pnl = dpos.realizedOrUnrealizedPnl(currentPriceTry);
        BigDecimal marketValue = (pnl != null ? entryNotional.add(pnl) : entryNotional)
                .abs().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        String key = ctx.byType() ? AssetType.VIOP.name() : dpos.getViopContract().getSymbol();
        acc.addBucket(key, AssetType.VIOP.name(), marketValue);
    }

    private List<AllocationItem> toAllocationItems(AllocationAcc acc) {
        BigDecimal denominator = acc.totalValue;
        BigDecimal finalCashCost = acc.cashCost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal finalCashRealized = acc.cashRealized.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        return acc.buckets.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> responseMapper.toAllocationItem(
                        e.getKey(),
                        acc.bucketTypes.get(e.getKey()),
                        e.getValue().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                        percentOf(e.getValue(), denominator),
                        CASH_KEY.equals(e.getKey()) ? finalCashCost : null,
                        CASH_KEY.equals(e.getKey()) ? finalCashRealized : null))
                .toList();
    }

    private static BigDecimal percentOf(BigDecimal value, BigDecimal denominator) {
        return denominator.compareTo(BigDecimal.ZERO) > 0
                ? value.multiply(HUNDRED).divide(denominator, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private List<AllocationItem> buildRealizedPnlAllocation(Long portfolioId, String assetTypeFilter) {
        boolean noFilter = assetTypeFilter == null || assetTypeFilter.isBlank();
        boolean groupByType = noFilter;
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        Map<String, BigDecimal> realizeds = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> realizedsByCurrency = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries = loadFxFrameSeries(portfolioId);
        boolean includeSpot = noFilter || !AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter);
        boolean includeViop = noFilter || AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter);
        if (includeSpot) accumulateClosedSpot(portfolioId, assetTypeFilter, noFilter, groupByType, costs, realizeds, realizedsByCurrency, types, fxSeries);
        if (includeViop) accumulateClosedDerivatives(portfolioId, groupByType, costs, realizeds, realizedsByCurrency, types, fxSeries);
        return toRealizedPnlItems(realizeds, realizedsByCurrency, costs, types);
    }

    private Map<String, TreeMap<LocalDate, BigDecimal>> loadFxFrameSeries(Long portfolioId) {
        LocalDate today = LocalDate.now();
        LocalDate oldest = positionRepository.findByPortfolioId(portfolioId).stream()
                .filter(PortfolioPosition::isClosed)
                .map(p -> p.getExitDate() != null ? p.getExitDate().toLocalDate() : null)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(today);
        Map<String, TreeMap<LocalDate, BigDecimal>> series = new LinkedHashMap<>();
        for (String currency : FRAME_CURRENCIES) {
            Map<LocalDate, BigDecimal> raw = historicalPricingPort.getPriceSeries(
                    MarketType.FOREX, currency, oldest.minusDays(14), today.plusDays(1));
            series.put(currency, raw != null && !raw.isEmpty() ? new TreeMap<>(raw) : new TreeMap<>());
        }
        return series;
    }

    private Map<String, BigDecimal> convertToFrames(BigDecimal realizedTry, LocalDate date,
                                                    Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        if (realizedTry == null) return Collections.emptyMap();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (var entry : fxSeries.entrySet()) {
            TreeMap<LocalDate, BigDecimal> series = entry.getValue();
            if (series.isEmpty()) continue;
            BigDecimal fxRate = null;
            if (date != null) {
                var floor = series.floorEntry(date);
                if (floor != null && floor.getValue() != null && floor.getValue().signum() > 0) {
                    fxRate = floor.getValue();
                }
            }
            if (fxRate == null) {
                var latest = series.lastEntry();
                if (latest != null && latest.getValue() != null && latest.getValue().signum() > 0) {
                    fxRate = latest.getValue();
                }
            }
            if (fxRate == null) continue;
            out.put(entry.getKey(), realizedTry.divide(fxRate, MoneyScale.PRICE, RoundingMode.HALF_UP));
        }
        return out;
    }

    private void mergeRealizedFrames(Map<String, Map<String, BigDecimal>> target, String key,
                                      Map<String, BigDecimal> increment) {
        if (increment.isEmpty()) return;
        target.computeIfAbsent(key, k -> new LinkedHashMap<>());
        Map<String, BigDecimal> bucket = target.get(key);
        for (var e : increment.entrySet()) {
            bucket.merge(e.getKey(), e.getValue(), BigDecimal::add);
        }
    }

    private void accumulateClosedSpot(Long portfolioId, String assetTypeFilter, boolean noFilter, boolean groupByType,
                                       Map<String, BigDecimal> costs, Map<String, BigDecimal> realizeds,
                                       Map<String, Map<String, BigDecimal>> realizedsByCurrency,
                                       Map<String, String> types,
                                       Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        for (PortfolioPosition pos : positionRepository.findByPortfolioId(portfolioId)) {
            if (!pos.isClosed() || pos.getExitPrice() == null) continue;
            if (!noFilter && !pos.getAssetType().name().equalsIgnoreCase(assetTypeFilter)) continue;
            BigDecimal realized = pos.realizedPnl();
            if (realized == null) continue;
            String key = groupByType ? pos.getAssetType().name() : pos.getAssetCode();
            costs.merge(key, pos.entryValue(), BigDecimal::add);
            realizeds.merge(key, realized, BigDecimal::add);
            types.putIfAbsent(key, pos.getAssetType().name());
            LocalDate exitDate = pos.getExitDate() != null ? pos.getExitDate().toLocalDate() : null;
            mergeRealizedFrames(realizedsByCurrency, key, convertToFrames(realized, exitDate, fxSeries));
        }
    }

    private void accumulateClosedDerivatives(Long portfolioId, boolean groupByType,
                                              Map<String, BigDecimal> costs, Map<String, BigDecimal> realizeds,
                                              Map<String, Map<String, BigDecimal>> realizedsByCurrency,
                                              Map<String, String> types,
                                              Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries) {
        for (DerivativePosition dpos : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (dpos.getViopContract() == null || dpos.isOpen()) continue;
            BigDecimal entryNotional = dpos.nominalExposure();
            BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
            if (entryNotional == null || realized == null) continue;
            String key = groupByType ? AssetType.VIOP.name() : dpos.getViopContract().getSymbol();
            costs.merge(key, entryNotional, BigDecimal::add);
            realizeds.merge(key, realized, BigDecimal::add);
            types.putIfAbsent(key, AssetType.VIOP.name());
            LocalDate closeDate = dpos.getCloseDate() != null ? dpos.getCloseDate() : null;
            mergeRealizedFrames(realizedsByCurrency, key, convertToFrames(realized, closeDate, fxSeries));
        }
    }

    private List<AllocationItem> toRealizedPnlItems(Map<String, BigDecimal> realizeds,
                                                     Map<String, Map<String, BigDecimal>> realizedsByCurrency,
                                                     Map<String, BigDecimal> costs,
                                                     Map<String, String> types) {
        BigDecimal absTotal = realizeds.values().stream()
                .map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        return realizeds.entrySet().stream()
                .sorted((a, b) -> b.getValue().abs().compareTo(a.getValue().abs()))
                .map(e -> {
                    BigDecimal abs = e.getValue().abs().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
                    BigDecimal pct = absTotal.signum() > 0
                            ? abs.multiply(HUNDRED).divide(absTotal, MoneyScale.PRICE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    Map<String, BigDecimal> byCurrency = realizedsByCurrency.getOrDefault(e.getKey(), Map.of());
                    return responseMapper.toAllocationItem(e.getKey(), types.get(e.getKey()), abs, pct,
                            costs.get(e.getKey()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                            e.getValue().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                            byCurrency);
                })
                .toList();
    }

    private List<AllocationItem> applyLimit(List<AllocationItem> items, Integer limit) {
        if (limit == null || limit <= 0 || items.size() <= limit) return items;
        List<AllocationItem> top = items.subList(0, limit - 1);
        List<AllocationItem> rest = items.subList(limit - 1, items.size());
        BigDecimal restValue = rest.stream().map(AllocationItem::valueTry).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restPercent = rest.stream().map(AllocationItem::percent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restCost = rest.stream().map(AllocationItem::costTry)
                .filter(c -> c != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal restRealized = rest.stream().map(AllocationItem::realizedPnlTry)
                .filter(r -> r != null).reduce(BigDecimal.ZERO, BigDecimal::add);
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
        BigDecimal rate = pricingPort.getExitPriceTry(MarketType.FOREX, currency.toUpperCase());
        return rate != null && rate.signum() > 0 ? nativePrice.multiply(rate) : nativePrice;
    }

    private List<PortfolioPosition> filterByType(List<PortfolioPosition> positions, String assetTypeName) {
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetTypeName, "enum.field.assetType");
        if (filterType == null) return positions;
        return positions.stream().filter(p -> p.getAssetType() == filterType).toList();
    }

    private List<AssetKey> toKeys(List<PortfolioPosition> positions) {
        return positions.stream().map(PortfolioPosition::toAssetKey).distinct().toList();
    }

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

    private static final class AllocationAcc {
        final Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        final Map<String, String> bucketTypes = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal cashTotal = BigDecimal.ZERO;
        BigDecimal cashCost = BigDecimal.ZERO;
        BigDecimal cashRealized = BigDecimal.ZERO;

        void addBucket(String key, String type, BigDecimal value) {
            buckets.merge(key, value, BigDecimal::add);
            bucketTypes.putIfAbsent(key, type);
            totalValue = totalValue.add(value);
        }

        void addCash(BigDecimal exitValue, BigDecimal entryValue, BigDecimal realized) {
            cashTotal = cashTotal.add(exitValue);
            if (entryValue != null) cashCost = cashCost.add(entryValue);
            if (realized != null) cashRealized = cashRealized.add(realized);
        }

        void appendCashBucket() {
            if (cashTotal.signum() == 0) return;
            buckets.merge(CASH_KEY, cashTotal, BigDecimal::add);
            bucketTypes.putIfAbsent(CASH_KEY, CASH_KEY);
            totalValue = totalValue.add(cashTotal);
        }
    }
}
