package com.finance.notification.market;

import com.finance.notification.market.session.MarketSession;
import com.finance.notification.market.session.SessionMarket;

import java.time.Instant;

public record MarketStatusResponse(
        SessionMarket market,
        MarketSession session,
        Instant nextTransitionAt) {

    public static MarketStatusResponse of(SessionMarket market, MarketSession session, Instant nextTransitionAt) {
        return new MarketStatusResponse(market, session, nextTransitionAt);
    }
}
