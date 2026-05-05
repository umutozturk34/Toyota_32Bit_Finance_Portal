package com.finance.notification.watchlist.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.WatchlistDeltaPayload;
import com.finance.notification.watchlist.mapper.WatchlistItemMapper;
import com.finance.notification.watchlist.mapper.WatchlistItemMapperImpl;
import com.finance.notification.watchlist.model.Watchlist;
import com.finance.notification.watchlist.model.WatchlistItem;
import com.finance.notification.watchlist.repository.WatchlistRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistEvaluatorTest {

    @Mock private WatchlistService watchlistService;
    @Mock private WatchlistRepository watchlistRepository;
    @Mock private NotificationDispatcher dispatcher;
    @Mock private AssetSnapshotCache assetSnapshotCache;

    private final WatchlistItemMapper watchlistItemMapper = new WatchlistItemMapperImpl();
    private WatchlistEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new WatchlistEvaluator(watchlistService, watchlistRepository, dispatcher,
                assetSnapshotCache, watchlistItemMapper);
        ReflectionTestUtils.setField(evaluator, "globalDeltaThreshold", BigDecimal.valueOf(5));
    }

    private WatchlistItem item(Long id, Long watchlistId, String userSub, String code,
                               BigDecimal lastSeen, BigDecimal threshold) {
        return WatchlistItem.builder()
                .id(id).watchlistId(watchlistId).userSub(userSub).marketType(MarketType.CRYPTO)
                .assetCode(code).lastSeenPrice(lastSeen).deltaThreshold(threshold).build();
    }

    private AssetSnapshot snapshot(String code, BigDecimal price) {
        return new AssetSnapshot(code, code + " name", "https://i.example/" + code + ".png", price, null, null);
    }

    private Watchlist list(Long id, String name) {
        return Watchlist.builder().id(id).name(name).userSub("user-1").isDefault(false).build();
    }

    @Test
    void evaluate_skipsWhenNoItems() {
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of());

        int notified = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(notified).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(assetSnapshotCache, never()).findByCodes(any(), any());
    }

    @Test
    void evaluate_recordsObservationWithoutDispatchOnFirstSighting() {
        WatchlistItem fresh = item(1L, 10L, "user-1", "BTC", null, null);
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of(fresh));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of("BTC", snapshot("BTC", BigDecimal.valueOf(100))));

        int notified = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(notified).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(watchlistService).persist(fresh);
        assertThat(fresh.getLastSeenPrice()).isEqualByComparingTo("100");
    }

    @Test
    void evaluate_batchesItemsOfSameWatchlistIntoSinglePayload() {
        WatchlistItem btc = item(1L, 10L, "user-1", "BTC", BigDecimal.valueOf(100), null);
        WatchlistItem eth = item(2L, 10L, "user-1", "ETH", BigDecimal.valueOf(100), null);
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of(btc, eth));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC", "ETH"))))
                .thenReturn(Map.of(
                        "BTC", snapshot("BTC", BigDecimal.valueOf(110)),
                        "ETH", snapshot("ETH", BigDecimal.valueOf(94))));
        when(watchlistRepository.findAllById(Set.of(10L)))
                .thenReturn(List.of(list(10L, "Favorilerim")));

        int notified = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(notified).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher, times(1)).dispatch(captor.capture());
        WatchlistDeltaPayload payload = (WatchlistDeltaPayload) captor.getValue().payload();
        assertThat(payload.watchlistId()).isEqualTo(10L);
        assertThat(payload.watchlistName()).isEqualTo("Favorilerim");
        assertThat(payload.items()).hasSize(2)
                .extracting(WatchlistDeltaPayload.DeltaItem::assetCode)
                .containsExactly("BTC", "ETH");
    }

    @Test
    void evaluate_separateDispatchesPerWatchlist() {
        WatchlistItem favBtc = item(1L, 10L, "user-1", "BTC", BigDecimal.valueOf(100), null);
        WatchlistItem moonBtc = item(2L, 11L, "user-1", "ETH", BigDecimal.valueOf(100), null);
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of(favBtc, moonBtc));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC", "ETH"))))
                .thenReturn(Map.of(
                        "BTC", snapshot("BTC", BigDecimal.valueOf(110)),
                        "ETH", snapshot("ETH", BigDecimal.valueOf(94))));
        when(watchlistRepository.findAllById(Set.of(10L, 11L)))
                .thenReturn(List.of(list(10L, "Fav"), list(11L, "Moon")));

        int notified = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(notified).isEqualTo(2);
        verify(dispatcher, times(2)).dispatch(any());
    }

    @Test
    void evaluate_doesNotDispatchWhenAllUnderThreshold() {
        WatchlistItem btc = item(1L, 10L, "user-1", "BTC", BigDecimal.valueOf(100), BigDecimal.valueOf(20));
        when(watchlistService.itemsForMarket(MarketType.CRYPTO)).thenReturn(List.of(btc));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of("BTC", snapshot("BTC", BigDecimal.valueOf(108))));

        int notified = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(notified).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(watchlistService).persist(btc);
    }
}
