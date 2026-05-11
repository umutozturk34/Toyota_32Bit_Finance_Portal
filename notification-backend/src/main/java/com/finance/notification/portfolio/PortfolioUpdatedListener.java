package com.finance.notification.portfolio;

import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.portfolio.PortfolioSnapshotReader.AggregatedSnapshot;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        FanoutResult result = fanoutService.fanoutBulk(
                "portfolio.updated",
                preferences::findPortfolioSubscribed,
                page -> buildPayloads(page, event.source()));
        log.info("portfolio.updated source={} dispatched={} failed={}",
                event.source(), result.dispatched(), result.failed());
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }

    private Map<String, PortfolioUpdatedPayload> buildPayloads(List<NotificationPreference> page, String source) {
        Set<String> subs = new HashSet<>(page.size());
        for (NotificationPreference pref : page) subs.add(pref.getUserSub());
        Map<String, AggregatedSnapshot> aggregates = snapshotReader.findTodayAggregateForUsers(subs);
        Map<String, PortfolioUpdatedPayload> payloads = new HashMap<>(page.size());
        for (NotificationPreference pref : page) {
            AggregatedSnapshot snap = aggregates.get(pref.getUserSub());
            if (snap == null || snap.totalValue() == null || snap.totalValue().signum() <= 0) continue;
            payloads.put(pref.getUserSub(), new PortfolioUpdatedPayload(
                    snap.totalValue(), snap.dailyPnl(), snap.dailyPnlPercent(), snap.portfolioCount(), source));
        }
        return payloads;
    }
}
