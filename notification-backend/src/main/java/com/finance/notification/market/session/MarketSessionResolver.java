package com.finance.notification.market.session;

import java.time.Instant;
import java.util.Optional;

/**
 * Determines whether a market is open or closed at a given instant and when its next open/close
 * transition occurs. Both methods return empty for markets with no configured schedule.
 */
public interface MarketSessionResolver {

    Optional<MarketSession> resolve(SessionMarket market, Instant at);

    Optional<Instant> nextTransition(SessionMarket market, Instant at);
}
