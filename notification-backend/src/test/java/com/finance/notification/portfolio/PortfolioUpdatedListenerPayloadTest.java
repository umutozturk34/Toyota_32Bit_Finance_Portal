package com.finance.notification.portfolio;

import com.finance.common.event.PortfolioUpdatedEvent;
import com.finance.notification.core.dispatch.NotificationFanoutService;
import com.finance.notification.core.dispatch.NotificationFanoutService.FanoutResult;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.portfolio.PortfolioSnapshotReader.PortfolioLine;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the private {@code buildPayloads} resolver by capturing the page-payload function passed to
 * {@code fanoutBulk} and invoking it directly: each user's portfolios are listed separately (name + status) under
 * a rolled-up header, while users with no portfolios or a non-positive combined value are skipped.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioUpdatedListenerPayloadTest {

    @Mock private NotificationFanoutService fanoutService;
    @Mock private NotificationPreferenceRepository preferences;
    @Mock private PortfolioSnapshotReader snapshotReader;
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
    void should_listEachPortfolioSeparatelyAndSkipEmpty_when_resolverInvoked() {
        when(processedEventIds.getIfPresent("evt-1")).thenReturn(null);
        when(fanoutService.fanoutBulk(anyString(), any(), any())).thenReturn(new FanoutResult(1, 0));
        NotificationPreference positive = pref("positive-user");
        NotificationPreference zero = pref("zero-user");
        NotificationPreference missing = pref("missing-user");
        when(snapshotReader.findTodayPerPortfolioForUsers(
                eq(Set.of("positive-user", "zero-user", "missing-user"))))
                .thenReturn(Map.of(
                        "positive-user", List.of(
                                new PortfolioLine(1L, "Spot", "SPOT", new BigDecimal("600"), new BigDecimal("30"), new BigDecimal("5")),
                                new PortfolioLine(2L, "VIOP", "SPOT", new BigDecimal("400"), new BigDecimal("20"), new BigDecimal("5"))),
                        "zero-user", List.of(
                                new PortfolioLine(3L, "Empty", "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, null))));

        listener.onPortfolioUpdated(new PortfolioUpdatedEvent("evt-1", OffsetDateTime.now(), "scheduler"), ack);

        verify(fanoutService).fanoutBulk(anyString(), any(), resolverCaptor.capture());
        Map<String, PortfolioUpdatedPayload> payloads =
                resolverCaptor.getValue().apply(List.of(positive, zero, missing));

        assertThat(payloads).containsOnlyKeys("positive-user");
        PortfolioUpdatedPayload payload = payloads.get("positive-user");
        assertThat(payload.totalValue()).isEqualByComparingTo("1000");
        assertThat(payload.dailyPnl()).isEqualByComparingTo("50");
        assertThat(payload.portfolioCount()).isEqualTo(2);
        assertThat(payload.source()).isEqualTo("scheduler");
        assertThat(payload.portfolios())
                .extracting(PortfolioUpdatedPayload.Line::name)
                .containsExactly("Spot", "VIOP");
    }
}
