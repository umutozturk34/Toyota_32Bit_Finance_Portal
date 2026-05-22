package com.finance.portfolio.service;

import com.finance.shared.service.AssetPricingPort;
import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.portfolio.dto.response.AssetAggregateResponse;
import com.finance.common.dto.response.PagedResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.model.MoneyScale;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.service.AssetPricingPort.AssetMeta;
import com.finance.shared.service.AssetPricingPort.PriceBundle;
import com.finance.shared.util.EnumParser;
import com.finance.shared.util.PercentChangeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioResponseMapper responseMapper;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final ViopCandleRepository viopCandleRepository;
    private final AllocationCalculator allocationCalculator;
    private final DerivativePositionFormatter derivativePositionFormatter;
    private final RealReturnCalculator realReturnCalculator;
    private final MultiCurrencyPnlCalculator multiCurrencyPnlCalculator;

    private BigDecimal latestCandleClose(String symbol) {
        if (symbol == null) return null;
        return viopCandleRepository.findFirstBySymbolOrderByCandleDateDesc(symbol)
                .map(ViopCandle::getClose)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        Map<AssetKey, PriceBundle> bundles = pricingPort.getExitBundles(toKeys(positions));
        Map<Long, BigDecimal> currentValuesById = currentValuesByPositionId(positions, bundles);
        Map<Long, BigDecimal> realPctById = realReturnCalculator.computePerPosition(positions, currentValuesById);
        List<PositionResponse> spotRows = positions.stream()
                .map(pos -> toPositionResponse(pos, bundles.get(pos.toAssetKey()), realPctById.get(pos.getId())))
                .toList();
        List<PositionResponse> derivativeRows = derivativePositionRepository.findByPortfolioId(portfolioId).stream()
                .map(this::toDerivativePositionResponse)
                .filter(r -> r != null)
                .toList();
        if (derivativeRows.isEmpty()) return spotRows;
        java.util.List<PositionResponse> combined = new java.util.ArrayList<>(spotRows.size() + derivativeRows.size());
        combined.addAll(spotRows);
        combined.addAll(derivativeRows);
        return combined;
    }

    private Map<Long, BigDecimal> currentValuesByPositionId(List<PortfolioPosition> positions,
                                                            Map<AssetKey, PriceBundle> bundles) {
        Map<Long, BigDecimal> out = new java.util.HashMap<>();
        for (PortfolioPosition pos : positions) {
            if (pos.getId() == null) continue;
            PriceBundle bundle = bundles.get(pos.toAssetKey());
            BigDecimal currentPrice = bundle != null && bundle.price() != null ? bundle.price() : BigDecimal.ZERO;
            BigDecimal effective = pos.isClosed() && pos.getExitPrice() != null ? pos.getExitPrice() : currentPrice;
            out.put(pos.getId(), pos.currentValue(effective));
        }
        return out;
    }

    private PositionResponse toDerivativePositionResponse(DerivativePosition position) {
        return derivativePositionFormatter.toPositionResponse(position);
    }

    private BigDecimal convertLiveToTry(BigDecimal nativePrice, String currency) {
        if (nativePrice == null) return null;
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) {
            return nativePrice;
        }
        BigDecimal rate = pricingPort.getExitPriceTry(
                com.finance.common.model.MarketType.FOREX, currency.toUpperCase());
        return rate != null && rate.signum() > 0 ? nativePrice.multiply(rate) : nativePrice;
    }

    @Transactional(readOnly = true)
    public PagedResponse<PositionResponse> getPositionsPaged(Long portfolioId, String search,
                                                               String assetType, String sortBy, String direction,
                                                               int page, int size) {
        List<PositionResponse> all = getPositions(portfolioId);

        if (assetType != null && !assetType.isBlank()) {
            all = all.stream().filter(r -> assetType.equalsIgnoreCase(r.assetType())).toList();
        }
        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            all = all.stream()
                    .filter(r -> r.assetCode().toLowerCase().contains(lower)
                            || (r.assetName() != null && r.assetName().toLowerCase().contains(lower)))
                    .toList();
        }
        if (sortBy != null && !sortBy.isBlank()) {
            Comparator<PositionResponse> comparator = buildPositionComparator(sortBy);
            all = "asc".equalsIgnoreCase(direction)
                    ? all.stream().sorted(comparator).toList()
                    : all.stream().sorted(comparator.reversed()).toList();
        }

        long total = all.size();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return PagedResponse.of(all.subList(from, to), page, size, total);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long portfolioId, String assetType) {
        List<PortfolioPosition> positions = filterByType(
                positionRepository.findByPortfolioId(portfolioId), assetType);
        SpotTotals spot = aggregateSpotTotals(positions);
        SummaryTotals totals = applyDerivativeAdjustment(portfolioId, assetType, spot);
        BigDecimal pnlPercent = totals.capitalBase.compareTo(BigDecimal.ZERO) > 0
                ? totals.totalPnl.multiply(HUNDRED).divide(totals.capitalBase, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "enum.field.assetType");
        BigDecimal dailyPnlAmount = aggregateDailyPnl(portfolioId, filterType);
        BigDecimal dailyPnlPercent = computeDailyPercent(totals.totalValue, dailyPnlAmount);
        RealReturnCalculator.RealReturnSummary real = realReturnCalculator.compute(positions, totals.totalValue);
        Map<String, com.finance.portfolio.dto.response.CurrencyFramePct> frames =
                multiCurrencyPnlCalculator.compute(positions, totals.totalValue, dailyPnlAmount,
                        pnlPercent, dailyPnlPercent);
        return responseMapper.toSummaryResponse(totals.totalValue, totals.totalEntry, totals.totalPnl,
                pnlPercent, dailyPnlAmount, dailyPnlPercent,
                real.realPnlTry(), real.realPnlPercent(), real.cpiGrowthPercent(), frames);
    }

    private BigDecimal computeDailyPercent(BigDecimal totalValue, BigDecimal dailyPnl) {
        if (totalValue == null || dailyPnl == null) return null;
        BigDecimal priorTotal = totalValue.subtract(dailyPnl);
        if (priorTotal.signum() <= 0) return null;
        return dailyPnl.multiply(HUNDRED).divide(priorTotal, MoneyScale.PRICE, RoundingMode.HALF_UP);
    }

    private SpotTotals aggregateSpotTotals(List<PortfolioPosition> positions) {
        List<PortfolioPosition> openPositions = positions.stream().filter(p -> !p.isClosed()).toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getExitPricesTry(toKeys(openPositions));
        SpotTotals spot = new SpotTotals();
        for (PortfolioPosition pos : positions) {
            if (pos.isClosed()) spot.addClosed(pos);
            else spot.addOpen(pos, prices.get(pos.toAssetKey()));
        }
        spot.scale();
        return spot;
    }

    private SummaryTotals applyDerivativeAdjustment(Long portfolioId, String assetType, SpotTotals spot) {
        BigDecimal totalValue = spot.spotValue.add(spot.closedExitValue);
        BigDecimal totalEntry = spot.openCost.add(spot.closedCost);
        BigDecimal totalPnl = totalValue.subtract(totalEntry).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal capitalBase = totalEntry;
        boolean noFilter = assetType == null || assetType.isBlank();
        boolean viopOnly = !noFilter && AssetType.VIOP.name().equalsIgnoreCase(assetType);
        if (!noFilter && !viopOnly) return new SummaryTotals(totalValue, totalEntry, totalPnl, capitalBase);
        BigDecimal derivativePnl = openDerivativePnl(portfolioId).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal derivativeNotional = openDerivativeNotional(portfolioId);
        if (viopOnly) {
            return new SummaryTotals(derivativeNotional.add(derivativePnl), derivativeNotional,
                    derivativePnl, derivativeNotional);
        }
        return new SummaryTotals(totalValue.add(derivativeNotional).add(derivativePnl),
                totalEntry.add(derivativeNotional), totalPnl.add(derivativePnl),
                capitalBase.add(derivativeNotional));
    }

    private static final class SpotTotals {
        BigDecimal spotValue = BigDecimal.ZERO;
        BigDecimal closedExitValue = BigDecimal.ZERO;
        BigDecimal openCost = BigDecimal.ZERO;
        BigDecimal closedCost = BigDecimal.ZERO;

        void addClosed(PortfolioPosition pos) {
            closedCost = closedCost.add(pos.entryValue());
            if (pos.getExitPrice() != null) {
                closedExitValue = closedExitValue.add(pos.getExitPrice().multiply(pos.getQuantity()));
            }
        }
        void addOpen(PortfolioPosition pos, BigDecimal price) {
            openCost = openCost.add(pos.entryValue());
            if (price != null) spotValue = spotValue.add(price.multiply(pos.getQuantity()));
        }
        void scale() {
            spotValue = spotValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            closedExitValue = closedExitValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            openCost = openCost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            closedCost = closedCost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        }
    }

    private record SummaryTotals(BigDecimal totalValue, BigDecimal totalEntry,
                                  BigDecimal totalPnl, BigDecimal capitalBase) {}

    private BigDecimal openDerivativePnl(Long portfolioId) {
        BigDecimal total = BigDecimal.ZERO;
        for (DerivativePosition position : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (position.getViopContract() == null) {
                continue;
            }
            BigDecimal latestClose = latestCandleClose(position.getViopContract().getSymbol());
            BigDecimal liveSource = latestClose != null
                    ? latestClose
                    : position.getViopContract().getLastPrice();
            BigDecimal currentTry = convertLiveToTry(liveSource,
                    position.getViopContract().getCurrency());
            BigDecimal pnl = position.realizedOrUnrealizedPnl(currentTry);
            if (pnl != null) {
                total = total.add(pnl);
            }
        }
        return total;
    }

    private BigDecimal openDerivativeNotional(Long portfolioId) {
        BigDecimal total = BigDecimal.ZERO;
        for (DerivativePosition position : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            BigDecimal notional = position.nominalExposure();
            if (notional != null) {
                total = total.add(notional.abs());
            }
        }
        return total;
    }

    private BigDecimal aggregateDailyPnl(Long portfolioId, AssetType assetType) {
        List<PortfolioAssetDailySnapshot> latestPerAsset = assetSnapshotRepository.findLatestPerAsset(portfolioId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioAssetDailySnapshot s : latestPerAsset) {
            if (assetType != null && s.getAssetType() != assetType) continue;
            if (s.getDailyPnlTry() == null) continue;
            totalAmount = totalAmount.add(s.getDailyPnlTry());
            any = true;
        }
        return any ? totalAmount.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP) : null;
    }

    @Transactional(readOnly = true)
    public AssetAggregateResponse getAssetAggregate(Long portfolioId, String assetTypeRaw, String assetCode) {
        AssetType assetType = EnumParser.parseOrBadRequest(AssetType.class, assetTypeRaw, "enum.field.assetType");
        List<PortfolioPosition> allLots = positionRepository.findByPortfolioId(portfolioId).stream()
                .filter(p -> p.getAssetType() == assetType)
                .filter(p -> assetCode.equalsIgnoreCase(p.getAssetCode()))
                .toList();
        if (allLots.isEmpty()) {
            return new AssetAggregateResponse(
                    assetType.name(), assetCode, null, null,
                    0, BigDecimal.ZERO, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<PortfolioPosition> openLots = allLots.stream().filter(p -> !p.isClosed()).toList();
        PortfolioPosition first = allLots.get(0);
        AssetKey key = first.toAssetKey();
        PriceBundle bundle = pricingPort.getExitBundle(key.type(), key.assetCode());
        BigDecimal currentPrice = bundle.price() != null ? bundle.price() : BigDecimal.ZERO;

        BigDecimal openQty = BigDecimal.ZERO;
        BigDecimal openEntryValue = BigDecimal.ZERO;
        BigDecimal openMarketValue = BigDecimal.ZERO;
        for (PortfolioPosition lot : openLots) {
            openQty = openQty.add(lot.getQuantity());
            openEntryValue = openEntryValue.add(lot.entryValue());
            openMarketValue = openMarketValue.add(lot.currentValue(currentPrice));
        }
        BigDecimal closedRealized = BigDecimal.ZERO;
        BigDecimal closedEntryValue = BigDecimal.ZERO;
        for (PortfolioPosition lot : allLots) {
            if (lot.isClosed() && lot.getExitPrice() != null) {
                closedRealized = closedRealized.add(
                        lot.getExitPrice().subtract(lot.getEntryPrice()).multiply(lot.getQuantity()));
                closedEntryValue = closedEntryValue.add(lot.entryValue());
            }
        }
        LocalDateTime earliest = null;
        for (PortfolioPosition lot : allLots) {
            if (earliest == null || (lot.getEntryDate() != null && lot.getEntryDate().isBefore(earliest))) {
                earliest = lot.getEntryDate();
            }
        }
        BigDecimal weightedAvg = openQty.signum() > 0
                ? openEntryValue.divide(openQty, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal unrealized = openMarketValue.subtract(openEntryValue);
        BigDecimal totalPnl = unrealized.add(closedRealized).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnlBasis = openEntryValue.add(closedEntryValue);
        BigDecimal pnlPercent = pnlBasis.signum() > 0
                ? totalPnl.multiply(new BigDecimal("100")).divide(pnlBasis, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        AssetMeta meta = bundle.meta() != null ? bundle.meta() : new AssetMeta(null, null);
        return new AssetAggregateResponse(
                assetType.name(), assetCode,
                meta.name(), meta.image(),
                openLots.size(), openQty, earliest, weightedAvg,
                currentPrice, openEntryValue, openMarketValue, totalPnl, pnlPercent);
    }

    @Transactional(readOnly = true)
    public List<AllocationItem> getAllocation(Long portfolioId, String mode, String assetTypeFilter, Integer limit) {
        return allocationCalculator.compute(portfolioId, mode, assetTypeFilter, limit);
    }

    private Comparator<PositionResponse> buildPositionComparator(String sortBy) {
        return switch (sortBy != null ? sortBy : "currentValue") {
            case "profitPercent" -> Comparator.comparing(PositionResponse::pnlPercent, Comparator.nullsLast(Comparator.naturalOrder()));
            case "profitAmount" -> Comparator.comparing(PositionResponse::pnlTry, Comparator.nullsLast(Comparator.naturalOrder()));
            case "assetCode" -> Comparator.comparing(PositionResponse::assetCode);
            case "quantity" -> Comparator.comparing(PositionResponse::quantity);
            case "entryDate" -> Comparator.comparing(PositionResponse::entryDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(PositionResponse::marketValueTry, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private PositionResponse toPositionResponse(PortfolioPosition pos, PriceBundle bundle, BigDecimal realPnlPercent) {
        PriceBundle effective = bundle != null ? bundle : new PriceBundle(null, new AssetMeta(null, null));
        BigDecimal marketPrice = effective.price() != null ? effective.price() : BigDecimal.ZERO;
        BigDecimal effectivePrice = pos.isClosed() && pos.getExitPrice() != null
                ? pos.getExitPrice()
                : marketPrice;
        BigDecimal entryValue = pos.entryValue();
        BigDecimal marketValue = pos.currentValue(effectivePrice);
        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(marketValue, entryValue, MoneyScale.PRICE);
        BigDecimal pnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
        AssetMeta meta = effective.meta() != null ? effective.meta() : new AssetMeta(null, null);
        return responseMapper.toPositionResponse(pos, effectivePrice, entryValue, marketValue, pnl, pnlPercent, realPnlPercent, meta.name(), meta.image());
    }

    private List<AssetKey> toKeys(List<PortfolioPosition> positions) {
        return positions.stream().map(PortfolioPosition::toAssetKey).toList();
    }

    private List<PortfolioPosition> filterByType(List<PortfolioPosition> positions, String assetTypeName) {
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetTypeName, "enum.field.assetType");
        if (filterType == null) return positions;
        return positions.stream().filter(p -> p.getAssetType() == filterType).toList();
    }

}
