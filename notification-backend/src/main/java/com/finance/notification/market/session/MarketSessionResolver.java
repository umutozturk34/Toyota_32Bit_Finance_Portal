package com.finance.notification.market.session;

import java.time.Instant;
import java.util.Optional;

public interface MarketSessionResolver {

    Optional<MarketSession> resolve(SessionMarket market, Instant at);

    Optional<Instant> nextTransition(SessionMarket market, Instant at);
}
