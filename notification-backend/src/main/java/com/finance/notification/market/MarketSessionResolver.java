package com.finance.notification.market;

import java.time.Instant;
import java.util.Optional;

public interface MarketSessionResolver {

    /**
     * Returns the current session for the given market at the supplied instant,
     * or empty when the market has no schedule configured (e.g. omitted from
     * application.yaml on purpose). Pure function — does not throw.
     */
    Optional<MarketSession> resolve(SessionMarket market, Instant at);

    /**
     * Returns the next scheduled transition (open or close boundary) strictly
     * after the supplied instant for the given market, or empty when the market
     * has no schedule configured.
     */
    Optional<Instant> nextTransition(SessionMarket market, Instant at);
}
