package com.finance.notification.portfolio;

import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.portfolio.PortfolioSnapshotReader.AggregatedSnapshot;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Exercises the private {@code buildPayloads} resolver by capturing the page-payload function passed
 * to {@code fanoutBulk} and invoking it directly, asserting that users with a positive snapshot
 * value get a payload while zero/null-value and missing-snapshot users are skipped.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioUpdatedListenerPayloadTest {

    @Mock private NotificationFanoutService fanoutService;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private PortfolioSnapshotReader snapshotReader;
    @SuppressWarnings("unchecked")
    @Mock private Cache<String, Boolean> processedEventIds;
    @Mock private Acknowledgment ack;

    @Captor
    private ArgumentCaptor<Function<List<NotificationPreference>, Map<String, PortfolioUpdatedPayload>>> resolverCaptor;

    private PortfolioUpdatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new PortfolioUpdatedListener(fanoutService, preferences, snapshotReader, processedEventIds);
    }

    private NotificationPreference pref(String userSub) {
        return NotificationPreference.builder().userSub(userSub).build();
    }

    @Test
    void should_buildPayloadsOnlyForUsersWithPositiveValue_when_resolverInvoked() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(fanoutService.fanoutBulk(anyString(), any(), any())).thenReturn(new FanoutResult(1, 0));
        NotificationPreference positive = pref("positive-user");
        NotificationPreference zero = pref("zero-user");
        NotificationPreference nullValue = pref("null-value-user");
        NotificationPreference missing = pref("missing-user");
        when(snapshotReader.findTodayAggregateForUsers(
                eq(Set.of("positive-user", "zero-user", "null-value-user", "missing-user"))))
                .thenReturn(Map.of(
                        "positive-user", new AggregatedSnapshot(
                                new BigDecimal("1000"), new BigDecimal("50"), new BigDecimal("5"), 2),
                        "zero-user", new AggregatedSnapshot(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1),
                        "null-value-user", new AggregatedSnapshot(
                                null, null, null, 1)));

        listener.onPortfolioUpdated(new PortfolioUpdatedEvent("evt-1", OffsetDateTime.now(), "scheduler"), ack);

        org.mockito.Mockito.verify(fanoutService)
                .fanoutBulk(anyString(), any(), resolverCaptor.capture());
        Map<String, PortfolioUpdatedPayload> payloads =
                resolverCaptor.getValue().apply(List.of(positive, zero, nullValue, missing));

        assertThat(payloads).containsOnlyKeys("positive-user");
        PortfolioUpdatedPayload payload = payloads.get("positive-user");
        assertThat(payload.totalValue()).isEqualByComparingTo("1000");
        assertThat(payload.portfolioCount()).isEqualTo(2);
        assertThat(payload.source()).isEqualTo("scheduler");
    }
}
