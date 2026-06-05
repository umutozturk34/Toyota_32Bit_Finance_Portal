package com.finance.portfolio.service.summary;

import com.finance.portfolio.model.MoneyScale;
import com.finance.portfolio.model.PortfolioPosition;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mutable accumulator for the spot (non-derivative) legs of a portfolio summary: open market value,
 * closed-lot exit cash, and open/closed cost. Package-private with package-visible fields so the
 * summary service can read the buckets after {@link SpotAggregationService} fills them.
 */
final class SpotTotals {
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
        BigDecimal entry = pos.entryValue();
        if (price == null) {
            // Asymmetric fold (entry counted, market value skipped) would silently produce a
            // false −100% loss on this position in the headline PnL. Better to count entry as
            // the market value too (treats the asset as "flat since purchase" when no price
            // resolves) — accurate enough until the live/snapshot fallback fills in.
            openCost = openCost.add(entry);
            spotValue = spotValue.add(entry);
            return;
        }
        openCost = openCost.add(entry);
        spotValue = spotValue.add(price.multiply(pos.getQuantity()));
    }

    void scale() {
        spotValue = spotValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        closedExitValue = closedExitValue.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        openCost = openCost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        closedCost = closedCost.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
    }
}
