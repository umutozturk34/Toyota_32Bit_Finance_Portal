package com.finance.notification.macro;

import com.finance.common.event.MacroIndicatorsUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.MacroIndicatorsUpdatedPayload;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.macro.MacroIndicatorChangeReader.IndicatorChange;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Kafka consumer for macro-indicators-updated events. Deduplicates by event id, re-reads the actual
 * indicator deltas (skipping fanout when nothing materially changed), then fans the digest out to
 * users subscribed to macro notifications.
 */
@Log4j2
@Component
public class MacroIndicatorsUpdatedListener {

    private static final String GROUP_ID = "notification-macro-indicators-updated";

    private final NotificationFanoutService fanoutService;
    private final NotificationPreferenceRepository preferences;
    private final MacroIndicatorChangeReader changeReader;
    private final Cache<String, Boolean> processedEventIds;

    public MacroIndicatorsUpdatedListener(NotificationFanoutService fanoutService,
                                          NotificationPreferenceRepository preferences,
                                          MacroIndicatorChangeReader changeReader,
                                          @Qualifier("macroIndicatorsUpdatedProcessedEventIds")
                                          Cache<String, Boolean> processedEventIds) {
        this.fanoutService = fanoutService;
        this.preferences = preferences;
        this.changeReader = changeReader;
        this.processedEventIds = processedEventIds;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.macro-indicators-updated}",
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory")
    public void onMacroIndicatorsUpdated(MacroIndicatorsUpdatedEvent event, Acknowledgment ack) {
        if (processedEventIds.getIfPresent(event.eventId()) != null) {
            log.debug("Duplicate macro.indicators.updated event {} source={}, skip",
                    event.eventId(), event.source());
            ack.acknowledge();
            return;
        }
        List<IndicatorChange> changes = changeReader.findChanges(event.changedCodes());
        if (changes.isEmpty()) {
            log.info("macro.indicators.updated source={} no actual deltas, skip fanout", event.source());
            processedEventIds.put(event.eventId(), Boolean.TRUE);
            ack.acknowledge();
            return;
        }
        MacroIndicatorsUpdatedPayload payload = new MacroIndicatorsUpdatedPayload(changes, event.source());
        FanoutResult result = fanoutService.fanout(
                "macro.indicators.updated",
                preferences::findMacroIndicatorsSubscribed,
                pref -> Optional.of(payload));
        log.info("macro.indicators.updated source={} changes={} dispatched={} failed={}",
                event.source(), changes.size(), result.dispatched(), result.failed());
        processedEventIds.put(event.eventId(), Boolean.TRUE);
        ack.acknowledge();
    }
}
