package com.finance.notification.portfolio;

import com.finance.common.event.KafkaTopics;
import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
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

    private final NotificationDispatcher dispatcher;
    private final Cache<String, Boolean> processedEventIds;

    public PortfolioUpdatedListener(NotificationDispatcher dispatcher,
                                    @Qualifier("portfolioUpdatedProcessedEventIds") Cache<String, Boolean> processedEventIds) {
        this.dispatcher = dispatcher;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = KafkaTopics.PORTFOLIO_UPDATED,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onPortfolioUpdated(PortfolioUpdatedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate portfolio.updated event {} for user {}, skip",
                    event.eventId(), event.userSub());
            ack.acknowledge();
            return;
        }
        if (event.userSub() == null || event.userSub().isBlank()) {
            log.warn("portfolio.updated event without userSub eventId={}, skip", event.eventId());
            processedEventIds.put(event.eventId(), Boolean.TRUE);
            ack.acknowledge();
            return;
        }
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                event.portfolioId(),
                event.snapshotId(),
                event.totalValue(),
                event.dailyPnl(),
                event.dailyPnlPercent());
        dispatcher.dispatch(NotificationRequest.of(event.userSub(), payload));
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
