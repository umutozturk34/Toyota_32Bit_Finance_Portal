package com.finance.portfolio.service.summary;

import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Buckets a portfolio's REALIZED gains (closed spot + closed derivatives) for the realized-PnL donut, ranking by
 * absolute realized magnitude with per-currency frames at each close's FX. Split out from the current-value
 * {@code AllocationCalculator} because it shares none of that path's live-price machinery (no live pricing port,
 * VIOP candle fallback, or snapshot lookup) — it reasons purely over closed positions — so the two are independent
 * computations that only happen to share the FX frame series and the response mapper.
 */
@Component
@RequiredArgsConstructor
class RealizedPnlAllocationCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PortfolioPositionRepository positionRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final PortfolioResponseMapper responseMapper;
    private final CurrencyFrameConverter frameConverter;
    private final AllocationFxFrameLoader fxFrameLoader;

    List<AllocationItem> build(Long portfolioId, String assetTypeFilter) {
        boolean noFilter = assetTypeFilter == null || assetTypeFilter.isBlank();
        boolean groupByType = noFilter;
        Map<String, BigDecimal> costs = new LinkedHashMap<>();
        Map<String, BigDecimal> realizeds = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> realizedsByCurrency = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> costsByCurrency = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        Map<String, TreeMap<LocalDate, BigDecimal>> fxSeries = fxFrameLoader.loadFxFrameSeries(portfolioId);
        boolean includeSpot = noFilter || !AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter);
        boolean includeViop = noFilter || AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter);
        if (includeSpot) accumulateClosedSpot(portfolioId, assetTypeFilter, noFilter, groupByType, costs, realizeds, realizedsByCurrency, costsByCurrency, types, fxSeries);
        if (includeViop) accumulateClosedDerivatives(portfolioId, groupByType, costs, realizeds, realizedsByCurrency, costsByCurrency, types, fxSeries);
        return toRealizedPnlItems(realizeds, realizedsByCurrency, costs, costsByCurrency, types);
    }

    private void accumulateClosedSpot(Long portfolioId, String assetTypeFilter, boolean noFilter, boolean groupByType,
                                       Map<String, BigDecimal> costs, Map<String, BigDecimal> realizeds,
                                       Map<String, Map<String, BigDecimal>> realizedsByCurrency,
                                       Map<String, Map<String, BigDecimal>> costsByCurrency,
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
            LocalDate entryDate = pos.getEntryDate() != null ? pos.getEntryDate().toLocalDate() : null;
            BigDecimal exitValue = pos.getExitPrice().multiply(pos.getQuantity())
                    .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            frameConverter.mergeRealizedFrames(realizedsByCurrency, key,
                    frameConverter.realizedFrames(exitValue, exitDate, pos.entryValue(), entryDate, fxSeries));
            frameConverter.mergeRealizedFrames(costsByCurrency, key, frameConverter.convertToFrames(pos.entryValue(), entryDate, fxSeries));
        }
    }

    private void accumulateClosedDerivatives(Long portfolioId, boolean groupByType,
                                              Map<String, BigDecimal> costs, Map<String, BigDecimal> realizeds,
                                              Map<String, Map<String, BigDecimal>> realizedsByCurrency,
                                              Map<String, Map<String, BigDecimal>> costsByCurrency,
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
            LocalDate closeDate = dpos.getCloseDate();
            LocalDate entryDate = dpos.getEntryDate();
            // Direction-aware per-currency realized: value the CLOSE notional at the close-date FX and the
            // entry notional at the entry-date FX, then flip the sign for a SHORT. Using proceeds (= entry +
            // realized) like a spot exit leaks the FX drift on the whole notional into a SHORT's realized,
            // reading its profit as a loss in USD/EUR while TRY is correct. closeNotional = price × size × lots.
            BigDecimal closeNotional = dpos.notionalAt(dpos.getClosePrice());
            int sign = dpos.getDirection() == DerivativeDirection.SHORT ? -1 : 1;
            frameConverter.mergeRealizedFrames(realizedsByCurrency, key, closeNotional != null
                    ? frameConverter.directionalRealizedFrames(closeNotional, closeDate, entryNotional, entryDate, sign, fxSeries)
                    : frameConverter.realizedFrames(realized.add(entryNotional), closeDate, entryNotional, entryDate, fxSeries));
            frameConverter.mergeRealizedFrames(costsByCurrency, key, frameConverter.convertToFrames(entryNotional, entryDate, fxSeries));
        }
    }

    private List<AllocationItem> toRealizedPnlItems(Map<String, BigDecimal> realizeds,
                                                     Map<String, Map<String, BigDecimal>> realizedsByCurrency,
                                                     Map<String, BigDecimal> costs,
                                                     Map<String, Map<String, BigDecimal>> costsByCurrency,
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
                    Map<String, BigDecimal> realizedByCurrency = realizedsByCurrency.getOrDefault(e.getKey(), Map.of());
                    Map<String, BigDecimal> costByCurrency = costsByCurrency.getOrDefault(e.getKey(), Map.of());
                    return responseMapper.toAllocationItem(e.getKey(), types.get(e.getKey()), abs, pct,
                            costs.get(e.getKey()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                            e.getValue().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                            realizedByCurrency, costByCurrency);
                })
                .toList();
    }
}
