package com.finance.portfolio.service.summary;

import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.service.AssetPricingPort.AssetMeta;
import com.finance.shared.service.AssetPricingPort.PriceBundle;
import com.finance.shared.util.EnumParser;
import com.finance.shared.util.PercentChangeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Builds and orders the unified {@link PositionResponse} rows for spot lots: maps a position + live
 * price bundle into a response, resolves the effective price (exit price for closed lots, live price
 * then last-snapshot fallback for open lots), and provides the stateless key/filter/sort helpers the
 * position-listing paths share. Holds no transaction of its own — callers run it inside their
 * read-only transaction.
 */
@Component
@RequiredArgsConstructor
class PositionRowBuilder {

    private final PortfolioResponseMapper responseMapper;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;

    /**
     * Maps a spot position into a response row. Closed lots are valued at their immutable exit price;
     * open lots use the live price, falling back to the last recorded snapshot price when the pricing
     * port returns null (stale Redis / a symbol Yahoo skipped today) — without the fallback the UI
     * would show 0 / "−100% loss" for an asset whose live price merely missed the cache.
     */
    PositionResponse toPositionResponse(PortfolioPosition pos, PriceBundle bundle, BigDecimal realPnlPercent) {
        PriceBundle effective = bundle != null ? bundle : new PriceBundle(null, new AssetMeta(null, null));
        BigDecimal effectivePrice = pos.isClosed() && pos.getExitPrice() != null
                ? pos.getExitPrice()
                : effective.price();
        if (effectivePrice == null && !pos.isClosed()) {
            effectivePrice = lastSnapshotPrice(pos);
        }
        BigDecimal entryValue = pos.entryValue();
        BigDecimal marketValue = effectivePrice != null ? pos.currentValue(effectivePrice) : null;
        PercentChangeCalculator.Result pct = marketValue != null
                ? PercentChangeCalculator.compute(marketValue, entryValue, MoneyScale.PRICE)
                : PercentChangeCalculator.Result.EMPTY;
        AssetMeta meta = effective.meta() != null ? effective.meta() : new AssetMeta(null, null);
        return responseMapper.toPositionResponse(pos, effectivePrice, entryValue, marketValue,
                pct.amount(), pct.percent(), realPnlPercent, meta.name(), meta.image());
    }

    /**
     * Most recent persistently-recorded unit price (in TRY) for this asset in this portfolio. The
     * {@code createdAt < now()+1day} window is load-bearing: it includes today's snapshot while
     * excluding any future-dated backfill row.
     */
    BigDecimal lastSnapshotPrice(PortfolioPosition pos) {
        if (pos.getPortfolio() == null || pos.getAssetType() == null || pos.getAssetCode() == null) return null;
        return assetSnapshotRepository
                .findFirstByPortfolioIdAndAssetTypeAndAssetCodeAndCreatedAtLessThanOrderByCreatedAtDesc(
                        pos.getPortfolio().getId(), pos.getAssetType(), pos.getAssetCode(),
                        LocalDateTime.now().plusDays(1))
                .map(PortfolioAssetDailySnapshot::getUnitPriceTry)
                .orElse(null);
    }

    /** Comparator for the requested sort key; defaults to current market value (nulls last). */
    Comparator<PositionResponse> buildPositionComparator(String sortBy) {
        return switch (sortBy != null ? sortBy : "currentValue") {
            case "profitPercent" -> Comparator.comparing(PositionResponse::pnlPercent, Comparator.nullsLast(Comparator.naturalOrder()));
            case "profitAmount" -> Comparator.comparing(PositionResponse::pnlTry, Comparator.nullsLast(Comparator.naturalOrder()));
            case "assetCode" -> Comparator.comparing(PositionResponse::assetCode);
            case "quantity" -> Comparator.comparing(PositionResponse::quantity);
            case "entryDate" -> Comparator.comparing(PositionResponse::entryDate, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(PositionResponse::marketValueTry, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    /** Pricing-port keys for the given positions. */
    List<AssetKey> toKeys(List<PortfolioPosition> positions) {
        return positions.stream().map(PortfolioPosition::toAssetKey).toList();
    }

    /** Filters to a single asset type when a parseable type name is given; otherwise returns all. */
    List<PortfolioPosition> filterByType(List<PortfolioPosition> positions, String assetTypeName) {
        AssetType filterType = EnumParser.parseNullable(AssetType.class, assetTypeName, "enum.field.assetType");
        if (filterType == null) return positions;
        return positions.stream().filter(p -> p.getAssetType() == filterType).toList();
    }
}
