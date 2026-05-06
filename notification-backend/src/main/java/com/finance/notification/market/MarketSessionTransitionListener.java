package com.finance.notification.market;

import com.finance.common.event.KafkaTopics;
import com.finance.common.event.MarketUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.MarketDataUpdatedPayload;
import com.finance.notification.core.dispatch.payload.MarketOpenedPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Subscribes to {@code market.updated} on a dedicated consumer group so the
 * existing alert/watchlist consumer is unaffected. For each event the listener
 * detects CLOSED→OPEN transitions for {@code MARKET_OPENED} and fires
 * {@code MARKET_DATA_UPDATED} on every event. Per-market opt-in is honoured
 * via {@link NotificationPreference#getMarketSessionMarkets()}.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class MarketSessionTransitionListener {

    private static final String GROUP_ID = "notification-session-transition";

    private final MarketSessionResolver resolver;
    private final MarketSessionTracker tracker;
    private final NotificationDispatcher dispatcher;
    private final NotificationPreferenceRepository preferences;
    private final Clock clock;

    @KafkaListener(
            topics = KafkaTopics.MARKET_UPDATED,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onMarketUpdated(MarketUpdatedEvent event, Acknowledgment ack) {
        try {
            handleSafely(event);
        } catch (RuntimeException ex) {
            log.error("Market session listener failed marketType={} eventId={}: {}",
                    event.marketType(), event.eventId(), ex.getMessage(), ex);
        } finally {
            ack.acknowledge();
        }
    }

    private void handleSafely(MarketUpdatedEvent event) {
        Optional<SessionMarket> mapped = MarketTypeMapper.fromMarketType(event.marketType());
        if (mapped.isEmpty()) {
            log.debug("Skipping session dispatch — no SessionMarket for {}", event.marketType());
            return;
        }
        SessionMarket market = mapped.get();
        List<NotificationPreference> subscribers = preferences.findAll();
        resolver.resolve(market, clock.instant()).ifPresent(current ->
                evaluateTransition(market, current, subscribers));
        dispatchDataUpdated(market, event.source(), subscribers);
    }

    private void evaluateTransition(SessionMarket market, MarketSession current,
                                    List<NotificationPreference> subscribers) {
        Optional<MarketSession> previous = tracker.previous(market);
        boolean openedNow = previous.map(prev -> prev == MarketSession.CLOSED && current == MarketSession.OPEN)
                .orElse(current == MarketSession.OPEN);
        tracker.update(market, current);
        if (!openedNow) return;
        log.info("Market session opened market={}; dispatching MARKET_OPENED", market);
        for (NotificationPreference pref : subscribers) {
            if (!isMarketSelected(pref, market)) continue;
            MarketOpenedPayload payload = new MarketOpenedPayload(market.name(), market.displayLabel());
            dispatcher.dispatch(NotificationRequest.of(pref.getUserSub(), payload));
        }
    }

    private void dispatchDataUpdated(SessionMarket market, String source,
                                     List<NotificationPreference> subscribers) {
        for (NotificationPreference pref : subscribers) {
            if (!isMarketSelected(pref, market)) continue;
            MarketDataUpdatedPayload payload = new MarketDataUpdatedPayload(market.name(), market.displayLabel(), source);
            dispatcher.dispatch(NotificationRequest.of(pref.getUserSub(), payload));
        }
    }

    private boolean isMarketSelected(NotificationPreference pref, SessionMarket market) {
        String selection = pref.getMarketSessionMarkets();
        if (selection == null || selection.isBlank()) return true;
        Set<String> selected = new HashSet<>(Arrays.asList(selection.split(",")));
        return selected.contains(market.name());
    }
}
