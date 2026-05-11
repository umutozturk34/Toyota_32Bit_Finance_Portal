package com.finance.notification.portfolio;

import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class PortfolioUpdatedListener {

    private static final String GROUP_ID = "notification-portfolio-updated";

    private final NotificationFanoutService fanoutService;
    private final NotificationPreferenceRepository preferences;
    private final PortfolioSnapshotReader snapshotReader;
    private final Cache<String, Boolean> processedEventIds;

    public PortfolioUpdatedListener(NotificationFanoutService fanoutService,
                                    NotificationPreferenceRepository preferences,
                                    PortfolioSnapshotReader snapshotReader,
                                    @Qualifier("portfolioUpdatedProcessedEventIds") Cache<String, Boolean> processedEventIds) {
        this.fanoutService = fanoutService;
        this.preferences = preferences;
        this.snapshotReader = snapshotReader;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.portfolio-updated}",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onPortfolioUpdated(PortfolioUpdatedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate portfolio.updated event {} source={}, skip",
                    event.eventId(), event.source());
            ack.acknowledge();
            return;
        }
        FanoutResult result = fanoutService.fanout(
                "portfolio.updated",
                preferences::findPortfolioSubscribed,
                pref -> snapshotReader.findTodayAggregateForUser(pref.getUserSub())
                        .filter(s -> s.totalValue() != null && s.totalValue().signum() > 0)
                        .map(s -> new PortfolioUpdatedPayload(
                                s.totalValue(), s.dailyPnl(), s.dailyPnlPercent(), s.portfolioCount(), event.source())));
        log.info("portfolio.updated source={} dispatched={} failed={}",
                event.source(), result.dispatched(), result.failed());
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
