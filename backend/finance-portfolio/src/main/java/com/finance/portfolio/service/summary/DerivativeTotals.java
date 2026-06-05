package com.finance.portfolio.service.summary;

import java.math.BigDecimal;

/**
 * Accumulator for a portfolio's derivative legs folded into the summary card: open absolute market
 * value, open entry notional, open unrealized PnL, and closed-lot exit cash + entry notional.
 * Package-private with package-visible fields so the summary service can read the buckets after
 * {@link DerivativeAggregationService} fills them.
 */
final class DerivativeTotals {
    BigDecimal openMarketValue = BigDecimal.ZERO;
    BigDecimal openEntryNotional = BigDecimal.ZERO;
    BigDecimal openPnl = BigDecimal.ZERO;
    BigDecimal closedExitValue = BigDecimal.ZERO;
    BigDecimal closedEntryNotional = BigDecimal.ZERO;
}
