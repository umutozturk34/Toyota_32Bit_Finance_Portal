package com.finance.backend.service;

import com.finance.backend.dto.response.AllocationItem;
import com.finance.backend.dto.response.PagedResponse;
import com.finance.backend.dto.response.PortfolioSummaryResponse;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.PortfolioPosition;
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

    private static final int SCALE = 4;

    private final AssetPricingPort pricingPort;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(Long portfolioId) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        Map<AssetKey, PriceBundle> bundles = pricingPort.getBundles(toKeys(positions));
        return positions.stream()
                .map(pos -> toPositionResponse(pos, bundles.get(toKey(pos))))
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
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetType, "asset type");
        if (filterType != null) {
            AssetType fixed = filterType;
            positions = positions.stream().filter(p -> p.getAssetType() == fixed).toList();
        }

        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(toKeys(positions));
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalEntryValue = BigDecimal.ZERO;
        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(toKey(pos));
            if (price != null) {
                totalValue = totalValue.add(price.multiply(pos.getQuantity()));
            }
            totalEntryValue = totalEntryValue.add(pos.entryValue());
        }
        totalValue = totalValue.setScale(SCALE, RoundingMode.HALF_UP);
        totalEntryValue = totalEntryValue.setScale(SCALE, RoundingMode.HALF_UP);

        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(totalValue, totalEntryValue, SCALE);
        BigDecimal totalPnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;

        return new PortfolioSummaryResponse(totalValue, totalEntryValue, totalPnl, pnlPercent);
    }

    @Transactional(readOnly = true)
    public List<AllocationItem> getAllocation(Long portfolioId, String mode, String assetTypeFilter) {
        List<PortfolioPosition> positions = positionRepository.findByPortfolioId(portfolioId);
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetTypeFilter, "asset type");
        if (filterType != null) {
            AssetType fixed = filterType;
            positions = positions.stream().filter(p -> p.getAssetType() == fixed).toList();
        }

        boolean byType = "assetType".equals(mode);
        Map<String, BigDecimal> buckets = new LinkedHashMap<>();
        Map<String, String> bucketTypes = new LinkedHashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(toKeys(positions));
        for (PortfolioPosition pos : positions) {
            BigDecimal price = prices.get(toKey(pos));
            BigDecimal marketValue = price != null
                    ? price.multiply(pos.getQuantity()).setScale(SCALE, RoundingMode.HALF_UP)
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
                        e.getValue().setScale(SCALE, RoundingMode.HALF_UP),
                        denominator.compareTo(BigDecimal.ZERO) > 0
                                ? e.getValue().multiply(new BigDecimal("100"))
                                        .divide(denominator, SCALE, RoundingMode.HALF_UP)
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
        PercentChangeCalculator.Result pct = PercentChangeCalculator.compute(marketValue, entryValue, SCALE);
        BigDecimal pnl = pct.amount() != null ? pct.amount() : BigDecimal.ZERO;
        BigDecimal pnlPercent = pct.percent() != null ? pct.percent() : BigDecimal.ZERO;
        AssetMeta meta = effective.meta() != null ? effective.meta() : new AssetMeta(null, null);
        return responseMapper.toPositionResponse(pos, currentPrice, entryValue, marketValue, pnl, pnlPercent, meta.name(), meta.image());
    }

    private List<AssetKey> toKeys(List<PortfolioPosition> positions) {
        return positions.stream().map(this::toKey).toList();
    }

    private AssetKey toKey(PortfolioPosition pos) {
        return new AssetKey(pos.getAssetType().marketType(), pos.getAssetCode());
    }
}
