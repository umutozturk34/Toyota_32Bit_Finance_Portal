package com.finance.backend.service;

import com.finance.backend.dto.response.AllocationItem;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.dto.response.PortfolioSummaryResponse;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioAssetDailySnapshot;
import com.finance.backend.model.PortfolioDailySnapshot;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.model.MoneyScale;
import com.finance.backend.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.backend.repository.PortfolioDailySnapshotRepository;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.service.AssetPricingPort.AssetKey;
import com.finance.backend.service.AssetPricingPort.AssetMeta;
import com.finance.backend.service.AssetPricingPort.PriceBundle;
import com.finance.backend.util.EnumParser;
import com.finance.backend.util.PercentChangeCalculator;
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
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        Map<AssetKey, PriceBundle> bundles = pricingPort.getBundles(toKeys(positions));
        return positions.stream()
                .map(pos -> toPositionResponse(pos, bundles.get(pos.toAssetKey())))
                .toList();
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

        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(toKeys(positions));
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalEntryValue = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(pos.toAssetKey());
            if (price != null) {
                totalValue = totalValue.add(price.multiply(pos.getQuantity()));
            }
            totalEntryValue = totalEntryValue.add(pos.entryValue());
        }
        totalValue = totalValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        totalEntryValue = totalEntryValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);

        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalValue, totalEntryValue, MoneyScale.PRICE);
        BigDecimal totalPnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;

        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "asset type");
        DailyPnlSummary daily = filterType == null
                ? aggregateDailyPnlOverall(portfolioId)
                : aggregateDailyPnlByAssetType(portfolioId, filterType);

        return new PortfolioSummaryResponse(totalValue, totalEntryValue, totalPnl, pnlPercent,
                daily.amount(), daily.percent());
    }

    private DailyPnlSummary aggregateDailyPnlOverall(Long portfolioId) {
        return dailySnapshotRepository.findFirstByPortfolioIdOrderByCreatedAtDesc(portfolioId)
                .map(s -> new DailyPnlSummary(s.getDailyPnlTry(), s.getDailyPnlPercent()))
                .orElse(DailyPnlSummary.EMPTY);
    }

    private DailyPnlSummary aggregateDailyPnlByAssetType(Long portfolioId, AssetType assetType) {
        List<PortfolioAssetDailySnapshot> latestPerAsset = assetSnapshotRepository.findLatestPerAsset(portfolioId);
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal priorTotal = BigDecimal.ZERO;
        BigDecimal currentTotal = BigDecimal.ZERO;
        boolean any = false;
        for (PortfolioAssetDailySnapshot s : latestPerAsset) {
            if (s.getAssetType() != assetType) continue;
            if (s.getDailyPnlTry() == null) continue;
            totalAmount = totalAmount.add(s.getDailyPnlTry());
            currentTotal = currentTotal.add(s.getMarketValueTry());
            priorTotal = priorTotal.add(s.getMarketValueTry().subtract(s.getDailyPnlTry()));
            any = true;
        }
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

        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(toKeys(positions));
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

        BigDecimal denominator = totalValue;
        return buckets.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new AllocationItem(
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
        PriceBundle effective = bundle != null ? bundle : new PriceBundle(null, null, new AssetMeta(null, null));
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
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetTypeName, "asset type");
        if (filterType == null) return positions;
        return positions.stream().filter(p -> p.getAssetType() == filterType).toList();
    }
}
