package com.finance.notification.watchlist.service;

import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.watchlist.model.WatchlistItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistEvaluatorTest {

    @Mock private WatchlistService watchlistService;
    @Mock private NotificationDispatcher dispatcher;

    private WatchlistEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new WatchlistEvaluator(watchlistService, dispatcher);
        ReflectionTestUtils.setField(evaluator, "globalDeltaThreshold", BigDecimal.valueOf(5));
    }

    private WatchlistItem itemWith(BigDecimal lastSeen, BigDecimal threshold) {
        return WatchlistItem.builder()
                .id(1L).userSub("user-1").marketType(MarketType.CRYPTO).assetCode("BTC")
                .lastSeenPrice(lastSeen).deltaThreshold(threshold).build();
    }

    @Test
    void evaluate_skipsWhenPricesEmpty() {
        int notified = evaluator.evaluate(MarketType.CRYPTO, Map.of());

        assertThat(notified).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void evaluate_recordsObservationWithoutDispatchOnFirstSighting() {
        WatchlistItem item = itemWith(null, null);
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of(item));

        int notified = evaluator.evaluate(MarketType.CRYPTO, Map.of("BTC", BigDecimal.valueOf(100)));

        assertThat(notified).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(watchlistService).persist(item);
        assertThat(item.getLastSeenPrice()).isEqualByComparingTo("100");
    }

    @Test
    void evaluate_dispatchesAndPersistsWhenDeltaCrossesGlobalThreshold() {
        WatchlistItem item = itemWith(BigDecimal.valueOf(100), null);
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of(item));

        int notified = evaluator.evaluate(MarketType.CRYPTO, Map.of("BTC", BigDecimal.valueOf(106)));

        assertThat(notified).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.WATCHLIST_DELTA);
        assertThat(captor.getValue().data()).containsEntry("assetCode", "BTC");
        verify(watchlistService).persist(item);
        assertThat(item.getLastSeenPrice()).isEqualByComparingTo("106");
    }

    @Test
    void evaluate_doesNotDispatchWhenDeltaUnderItemThreshold() {
        WatchlistItem item = itemWith(BigDecimal.valueOf(100), BigDecimal.valueOf(10));
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of(item));

        int notified = evaluator.evaluate(MarketType.CRYPTO, Map.of("BTC", BigDecimal.valueOf(108)));

        assertThat(notified).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(watchlistService).persist(item);
    }
}
