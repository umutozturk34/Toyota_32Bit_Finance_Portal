package com.finance.notification.market;

import com.finance.notification.market.session.*;

import java.time.Instant;

public record MarketStatusResponse(
        SessionMarket market,
        String displayLabel,
        MarketSession session,
        Instant nextTransitionAt) {

    public static MarketStatusResponse of(SessionMarket market, MarketSession session, Instant nextTransitionAt) {
        return new MarketStatusResponse(market, market.displayLabel(), session, nextTransitionAt);
    }
}
