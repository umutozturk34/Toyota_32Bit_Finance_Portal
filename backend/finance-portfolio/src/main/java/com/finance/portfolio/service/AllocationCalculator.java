package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    List<AllocationItem> compute(Long portfolioId, String mode, String assetTypeFilter, Integer limit) {
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
        Map<String, String> types = new LinkedHashMap<>();
        boolean includeSpot = noFilter || !AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter);
        boolean includeViop = noFilter || AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter);
        if (includeSpot) accumulateClosedSpot(portfolioId, assetTypeFilter, noFilter, groupByType, costs, realizeds, types);
        if (includeViop) accumulateClosedDerivatives(portfolioId, groupByType, costs, realizeds, types);
        return toRealizedPnlItems(realizeds, costs, types);
    }

    private void accumulateClosedSpot(Long portfolioId, String assetTypeFilter, boolean noFilter, boolean groupByType,
                                       Map<String, BigDecimal> costs, Map<String, BigDecimal> realizeds,
                                       Map<String, String> types) {
        for (PortfolioPosition pos : positionRepository.findByPortfolioId(portfolioId)) {
            if (!pos.isClosed() || pos.getExitPrice() == null) continue;
            if (!noFilter && !pos.getAssetType().name().equalsIgnoreCase(assetTypeFilter)) continue;
            BigDecimal realized = pos.realizedPnl();
            if (realized == null) continue;
            String key = groupByType ? pos.getAssetType().name() : pos.getAssetCode();
            costs.merge(key, pos.entryValue(), BigDecimal::add);
            realizeds.merge(key, realized, BigDecimal::add);
            types.putIfAbsent(key, pos.getAssetType().name());
        }
    }

    private void accumulateClosedDerivatives(Long portfolioId, boolean groupByType,
                                              Map<String, BigDecimal> costs, Map<String, BigDecimal> realizeds,
                                              Map<String, String> types) {
        for (DerivativePosition dpos : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (dpos.getViopContract() == null || dpos.isOpen()) continue;
            BigDecimal entryNotional = dpos.nominalExposure();
            BigDecimal realized = dpos.realizedOrUnrealizedPnl(dpos.getClosePrice());
            if (entryNotional == null || realized == null) continue;
            String key = groupByType ? AssetType.VIOP.name() : dpos.getViopContract().getSymbol();
            costs.merge(key, entryNotional, BigDecimal::add);
            realizeds.merge(key, realized, BigDecimal::add);
            types.putIfAbsent(key, AssetType.VIOP.name());
        }
    }

    private List<AllocationItem> toRealizedPnlItems(Map<String, BigDecimal> realizeds,
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
                    return responseMapper.toAllocationItem(e.getKey(), types.get(e.getKey()), abs, pct,
                            costs.get(e.getKey()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                            e.getValue().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP));
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
