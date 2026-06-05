package com.finance.portfolio.service.summary;

import com.finance.portfolio.model.PortfolioPosition;
import com.finance.shared.service.AssetPricingPort;
import com.finance.shared.service.AssetPricingPort.AssetKey;
import com.finance.shared.service.AssetPricingPort.PriceBundle;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Values the spot (non-derivative) legs of a portfolio. Open lots are marked-to-market at the live
 * SELLING price with a last-snapshot fallback; closed lots carry their stored exit cash. Runs inside
 * the caller's read-only transaction (no transaction of its own).
 */
@Log4j2
@Component
@RequiredArgsConstructor
class SpotAggregationService {

    private final AssetPricingPort pricingPort;
    private final PositionRowBuilder positionRowBuilder;

    /**
     * Folds every spot position into a {@link SpotTotals}: open positions are marked-to-market at the
     * CURRENT price (forex SELLING rate, matching the daily snapshot/chart so the card and the
     * Performance chart agree), with a last-snapshot fallback when the live price is missing so a
     * cache miss does not silently drop a position from the total. Closed lots add their exit cash.
     */
    SpotTotals aggregateSpotTotals(List<PortfolioPosition> positions) {
        List<PortfolioPosition> openPositions = positions.stream().filter(p -> !p.isClosed()).toList();
        Map<AssetKey, BigDecimal> prices = pricingPort.getPricesTry(positionRowBuilder.toKeys(openPositions));
        SpotTotals spot = new SpotTotals();
        for (PortfolioPosition pos : positions) {
            if (pos.isClosed()) {
                spot.addClosed(pos);
                continue;
            }
            BigDecimal price = prices.get(pos.toAssetKey());
            if (price == null) {
                price = positionRowBuilder.lastSnapshotPrice(pos);
                log.debug("Live price missing for {}:{} — using last-snapshot fallback price={}",
                        pos.getAssetType(), pos.getAssetCode(), price);
            }
            spot.addOpen(pos, price);
        }
        spot.scale();
        return spot;
    }

    /**
     * Current TRY value per position id, used to feed the real-return calculator. Mirrors the
     * last-snapshot fallback so the per-position real-return % stays in sync with the displayed market
     * value — without it an asset with a missing live price would show correct nominal PnL but a
     * spurious "−100% real" from a zero current value.
     */
    Map<Long, BigDecimal> currentValuesByPositionId(List<PortfolioPosition> positions,
                                                    Map<AssetKey, PriceBundle> bundles) {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (PortfolioPosition pos : positions) {
            if (pos.getId() == null) continue;
            PriceBundle bundle = bundles.get(pos.toAssetKey());
            BigDecimal currentPrice = bundle != null ? bundle.price() : null;
            if (currentPrice == null && !pos.isClosed()) currentPrice = positionRowBuilder.lastSnapshotPrice(pos);
            BigDecimal effective = pos.isClosed() && pos.getExitPrice() != null ? pos.getExitPrice() : currentPrice;
            if (effective == null) continue;
            out.put(pos.getId(), pos.currentValue(effective));
        }
        return out;
    }
}
