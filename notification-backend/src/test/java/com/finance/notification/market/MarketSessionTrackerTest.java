package com.finance.notification.market;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MarketSessionTrackerTest {

    @Test
    void should_returnEmpty_when_noPreviousState() {
        MarketSessionTracker tracker = new MarketSessionTracker();

        Optional<MarketSession> previous = tracker.previous(SessionMarket.STOCK);

        assertThat(previous).isEmpty();
    }

    @Test
    void should_returnLatestSession_when_updated() {
        MarketSessionTracker tracker = new MarketSessionTracker();

        tracker.update(SessionMarket.STOCK, MarketSession.OPEN);
        tracker.update(SessionMarket.STOCK, MarketSession.CLOSED);

        assertThat(tracker.previous(SessionMarket.STOCK)).contains(MarketSession.CLOSED);
    }

    @Test
    void should_isolateMarkets_when_updateOneDoesNotAffectOther() {
        MarketSessionTracker tracker = new MarketSessionTracker();

        tracker.update(SessionMarket.STOCK, MarketSession.OPEN);

        assertThat(tracker.previous(SessionMarket.STOCK)).contains(MarketSession.OPEN);
        assertThat(tracker.previous(SessionMarket.FUND)).isEmpty();
    }
}
