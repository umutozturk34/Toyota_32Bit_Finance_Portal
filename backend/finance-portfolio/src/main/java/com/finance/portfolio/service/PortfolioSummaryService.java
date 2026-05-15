package com.finance.portfolio.service;
import com.finance.shared.service.AssetPricingPort;



import com.finance.portfolio.dto.response.AllocationItem;
import com.finance.common.dto.response.PagedResponse;
import com.finance.portfolio.dto.response.PortfolioSummaryResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.model.MoneyScale;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioSummaryService {


    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioResponseMapper responseMapper;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final DerivativePositionRepository derivativePositionRepository;

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        Map<AssetKey, PriceBundle> bundles = pricingPort.getExitBundles(toKeys(positions));
        List<PositionResponse> spotRows = positions.stream()
                .map(pos -> toPositionResponse(pos, bundles.get(pos.toAssetKey())))
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

    private PositionResponse toDerivativePositionResponse(DerivativePosition position) {
        if (position.getViopContract() == null) return null;
        BigDecimal contractSize = position.getViopContract().getContractSize() != null
                ? position.getViopContract().getContractSize() : BigDecimal.ONE;
        BigDecimal qty = position.getQuantityLot() != null ? position.getQuantityLot() : BigDecimal.ZERO;
        // entry_price and close_price are TRY-canonical in DB (frontend submits TRY, scaling).
        BigDecimal entryPrice = position.getEntryPrice() != null ? position.getEntryPrice() : BigDecimal.ZERO;
        boolean closed = !position.isOpen();
        BigDecimal currentPriceTry = closed
                ? position.getClosePrice()
                : convertLiveToTry(position.getViopContract().getLastPrice(),
                        position.getViopContract().getCurrency());
        BigDecimal entryNotional = entryPrice.multiply(contractSize).multiply(qty)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnl = position.realizedOrUnrealizedPnl(currentPriceTry);
        if (pnl == null) pnl = BigDecimal.ZERO;
        pnl = pnl.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal marketValue = entryNotional.add(pnl).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = entryNotional.signum() > 0
                ? pnl.multiply(new BigDecimal("100")).divide(entryNotional, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String statusTag = closed ? " · KAPALI" : "";
        String name = position.getDirection().name() + " · " + position.getViopContract().getSymbol() + statusTag;
        return new PositionResponse(
                position.getId(),
                AssetType.VIOP.name(),
                position.getViopContract().getSymbol(),
                name,
                null,
                qty,
                position.getEntryDate() != null ? position.getEntryDate().atStartOfDay() : null,
                entryPrice,
                currentPriceTry,
                entryNotional,
                marketValue,
                pnl,
                pnlPercent);
    }

    /**
     * Converts the contract's live last_price from its native quote currency to TRY using the
     * current FOREX strategy rate. TRY-quoted contracts pass through. Used at read time only —
     * entry_price and close_price are already TRY in DB.
     */
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

        Map<AssetKey, BigDecimal> prices = pricingPort.getExitPricesTry(toKeys(positions));
        BigDecimal spotValue = BigDecimal.ZERO;
        BigDecimal totalEntryValue = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(pos.toAssetKey());
            if (price != null) {
                spotValue = spotValue.add(price.multiply(pos.getQuantity()));
            }
            totalEntryValue = totalEntryValue.add(pos.entryValue());
        }
        spotValue = spotValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        totalEntryValue = totalEntryValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);

        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(spotValue, totalEntryValue, MoneyScale.PRICE);
        BigDecimal totalValue = spotValue;
        BigDecimal totalPnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;

        BigDecimal capitalBase = totalEntryValue;
        if (assetType == null || assetType.isBlank()) {
            BigDecimal derivativePnl = openDerivativePnl(portfolioId).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal derivativeNotional = openDerivativeNotional(portfolioId);
            totalValue = totalValue.add(derivativeNotional).add(derivativePnl);
            totalEntryValue = totalEntryValue.add(derivativeNotional);
            totalPnl = totalPnl.add(derivativePnl);
            capitalBase = capitalBase.add(derivativeNotional);
        } else if (AssetType.VIOP.name().equalsIgnoreCase(assetType)) {
            BigDecimal derivativePnl = openDerivativePnl(portfolioId).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
            BigDecimal derivativeNotional = openDerivativeNotional(portfolioId);
            totalValue = derivativeNotional.add(derivativePnl);
            totalPnl = derivativePnl;
            totalEntryValue = derivativeNotional;
            capitalBase = derivativeNotional;
        }

        BigDecimal pnlPercent = capitalBase.signum() > 0
                ? totalPnl.multiply(new BigDecimal("100")).divide(capitalBase, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : (pct.percent() != null ? pct.percent() : BigDecimal.ZERO);

        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "enum.field.assetType");
        DailyPnlSummary daily = aggregateDailyPnl(portfolioId, filterType);

        return responseMapper.toSummaryResponse(totalValue, totalEntryValue, totalPnl, pnlPercent,
                daily.amount(), daily.percent());
    }

    /**
     * Combined VIOP P&L: mark-to-market for open positions (live last_price), realized for
     * closed positions (locked close_price via entity rule). Closed positions stay in the
     * total so realized gains/losses keep contributing to the portfolio after close.
     */
    private BigDecimal openDerivativePnl(Long portfolioId) {
        BigDecimal total = BigDecimal.ZERO;
        for (DerivativePosition position : derivativePositionRepository.findByPortfolioId(portfolioId)) {
            if (position.getViopContract() == null) {
                continue;
            }
            // entry_price already TRY, lastPrice native → convert to TRY before pnl calc.
            BigDecimal currentTry = convertLiveToTry(position.getViopContract().getLastPrice(),
                    position.getViopContract().getCurrency());
            BigDecimal pnl = position.realizedOrUnrealizedPnl(currentTry);
            if (pnl != null) {
                total = total.add(pnl);
            }
        }
        return total;
    }

    /**
     * Notional exposure (entryPrice × contractSize × lot) of every derivative position — open
     * AND closed. Closed positions stay in the cost basis so the hypothetical-portfolio totals
     * keep treating them as occupying capital until the user explicitly deletes them.
     */
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

    private DailyPnlSummary aggregateDailyPnl(Long portfolioId, AssetType assetType) {
        List<PortfolioAssetDailySnapshot> latestPerAsset = assetSnapshotRepository.findLatestPerAsset(portfolioId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal priorTotal = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioAssetDailySnapshot s : latestPerAsset) {
            if (assetType != null && s.getAssetType() != assetType) continue;
            if (s.getDailyPnlTry() == null) continue;
            totalAmount = totalAmount.add(s.getDailyPnlTry());
            priorTotal = priorTotal.add(s.getMarketValueTry().subtract(s.getDailyPnlTry()));
            any = true;
        }
        // VIOP rows feed in via latestPerAsset (assetType=VIOP, persisted dailyPnlTry). Their
        // marketValue contribution to priorTotal makes the daily-% denominator self-consistent;
        // no extra margin top-up needed now that derivative snapshots are persisted (V90).
        if (!any) return DailyPnlSummary.EMPTY;
        BigDecimal percent = priorTotal.compareTo(BigDecimal.ZERO) > 0
                ? totalAmount.multiply(new BigDecimal("100")).divide(priorTotal, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : null;
        return new DailyPnlSummary(totalAmount.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP), percent);
    }

    private record DailyPnlSummary(BigDecimal amount, BigDecimal percent) {
        static final DailyPnlSummary EMPTY = new DailyPnlSummary(null, null);
    }

    @Transactional(readOnly = true)
    public List<AllocationItem> getAllocation(Long portfolioId, String mode, String assetTypeFilter) {
        List<PortfolioPosition> positions = filterByType(
                positionRepository.findByPortfolioId(portfolioId), assetTypeFilter);

        boolean byType = "assetType".equals(mode);
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        Map<String, String> bucketTypes = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        Map<AssetKey, BigDecimal> prices = pricingPort.getExitPricesTry(toKeys(positions));
        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(pos.toAssetKey());
            BigDecimal marketValue = price != null
                    ? price.multiply(pos.getQuantity()).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            String key = byType ? pos.getAssetType().name() : pos.getAssetCode();
            buckets.merge(key, marketValue, BigDecimal::add);
            bucketTypes.putIfAbsent(key, pos.getAssetType().name());
            totalValue = totalValue.add(marketValue);
        }

        boolean includeDerivatives = assetTypeFilter == null || assetTypeFilter.isBlank()
                || AssetType.VIOP.name().equalsIgnoreCase(assetTypeFilter)
                || "FUTURE".equalsIgnoreCase(assetTypeFilter) || "OPTION".equalsIgnoreCase(assetTypeFilter);
        if (includeDerivatives) {
            for (DerivativePosition dpos : derivativePositionRepository.findByPortfolioId(portfolioId)) {
                if (dpos.getViopContract() == null) continue;
                String kindName = dpos.getViopContract().getKind().name();
                if (("FUTURE".equalsIgnoreCase(assetTypeFilter) || "OPTION".equalsIgnoreCase(assetTypeFilter))
                        && !assetTypeFilter.equalsIgnoreCase(kindName)) continue;
                BigDecimal entryNotional = dpos.nominalExposure();
                if (entryNotional == null) continue;
                BigDecimal currentPriceTry = !dpos.isOpen()
                        ? dpos.getClosePrice()
                        : convertLiveToTry(dpos.getViopContract().getLastPrice(),
                                dpos.getViopContract().getCurrency());
                BigDecimal pnl = dpos.realizedOrUnrealizedPnl(currentPriceTry);
                BigDecimal marketValue = pnl != null ? entryNotional.add(pnl) : entryNotional;
                marketValue = marketValue.abs().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
                String key = byType ? AssetType.VIOP.name() : dpos.getViopContract().getSymbol();
                buckets.merge(key, marketValue, BigDecimal::add);
                bucketTypes.putIfAbsent(key, AssetType.VIOP.name());
                totalValue = totalValue.add(marketValue);
            }
        }

        BigDecimal denominator = totalValue;
        return buckets.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> responseMapper.toAllocationItem(
                        e.getKey(),
                        bucketTypes.get(e.getKey()),
                        e.getValue().setScale(MoneyScale.PRICE, RoundingMode.HALF_UP),
                        denominator.compareTo(BigDecimal.ZERO) > 0
                                ? e.getValue().multiply(new BigDecimal("100"))
                                        .divide(denominator, MoneyScale.PRICE, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO
                ))
                .toList();
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

    private PositionResponse toPositionResponse(PortfolioPosition pos, PriceBundle bundle) {
        PriceBundle effective = bundle != null ? bundle : new PriceBundle(null, new AssetMeta(null, null));
        BigDecimal currentPrice = effective.price() != null ? effective.price() : BigDecimal.ZERO;
        BigDecimal entryValue = pos.entryValue();
        BigDecimal marketValue = pos.currentValue(currentPrice);
        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(marketValue, entryValue, MoneyScale.PRICE);
        BigDecimal pnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
        AssetMeta meta = effective.meta() != null ? effective.meta() : new AssetMeta(null, null);
        return responseMapper.toPositionResponse(pos, currentPrice, entryValue, marketValue, pnl, pnlPercent, meta.name(), meta.image());
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
