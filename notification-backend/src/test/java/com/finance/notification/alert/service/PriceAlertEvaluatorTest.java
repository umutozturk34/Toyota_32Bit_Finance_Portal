package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.mapper.PriceAlertMapper;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationDispatcher.BatchResult;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertEvaluatorTest {

    @Mock private PriceAlertService alertService;
    @Mock private NotificationDispatcher dispatcher;
    @Mock private AssetSnapshotCache assetSnapshotCache;
    @Mock private PriceAlertMapper priceAlertMapper;

    @InjectMocks
    private PriceAlertEvaluator evaluator;

    @BeforeEach
    void setUp() {
        // @Value field is not populated by @InjectMocks; a page larger than any test's alert set keeps the
        // single-page tests on one scan iteration (the keyset loop ends once a partial page comes back).
        ReflectionTestUtils.setField(evaluator, "scanPageSize", 500);
    }

    private PriceAlert alertFor(String code, AlertDirection dir, BigDecimal thr, BigDecimal ref) {
        return PriceAlert.builder()
                .id(1L).userSub("user-1").marketType(MarketType.CRYPTO).assetCode(code)
                .direction(dir).threshold(thr).referencePrice(ref).currency("TRY").active(true).build();
    }

    private AssetSnapshot snapshot(String code, BigDecimal price) {
        return new AssetSnapshot(code, code, "https://i.example/" + code + ".png", price, null, null);
    }

    @Test
    void should_returnZero_when_noActiveAlerts() {
        when(alertService.activeAlertsAfter(eq(MarketType.CRYPTO), eq(0L), anyInt())).thenReturn(List.of());

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatchBatched(any());
        verify(assetSnapshotCache, never()).findByCodes(any(), any());
    }

    @Test
    void should_dispatchBatchedWithMappedPayload_when_aboveThresholdTriggered() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        AssetSnapshot snap = snapshot("BTC", BigDecimal.valueOf(105));
        PriceAlertPayload mapped = new PriceAlertPayload(
                1L, MarketType.CRYPTO, "BTC", AlertDirection.ABOVE,
                BigDecimal.valueOf(100), BigDecimal.valueOf(105), "image", "BTC name", "TRY");
        when(alertService.activeAlertsAfter(eq(MarketType.CRYPTO), eq(0L), anyInt())).thenReturn(List.of(alert));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of("BTC", snap));
        when(priceAlertMapper.toFiredPayload(alert, snap, MarketType.CRYPTO)).thenReturn(mapped);
        when(dispatcher.dispatchBatched(any())).thenReturn(new BatchResult(1, 0));

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(1);
        assertThat(alert.getTriggeredAt()).isNotNull();
        assertThat(alert.isActive()).isFalse();
        verify(alertService).persist(alert);

        ArgumentCaptor<List<NotificationRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchBatched(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        NotificationRequest captured = captor.getValue().get(0);
        assertThat(captured.type()).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
        assertThat(captured.userSub()).isEqualTo("user-1");
        assertThat(captured.payload()).isSameAs(mapped);
    }

    @Test
    void should_dispatchSurvivingFire_when_oneAlertPersistThrows() {
        PriceAlert bad = PriceAlert.builder()
                .id(1L).userSub("user-1").marketType(MarketType.CRYPTO).assetCode("BTC")
                .direction(AlertDirection.ABOVE).threshold(BigDecimal.valueOf(100))
                .currency("TRY").active(true).build();
        PriceAlert good = PriceAlert.builder()
                .id(2L).userSub("user-2").marketType(MarketType.CRYPTO).assetCode("ETH")
                .direction(AlertDirection.ABOVE).threshold(BigDecimal.valueOf(100))
                .currency("TRY").active(true).build();
        AssetSnapshot btc = snapshot("BTC", BigDecimal.valueOf(105));
        AssetSnapshot eth = snapshot("ETH", BigDecimal.valueOf(110));
        PriceAlertPayload goodPayload = new PriceAlertPayload(
                2L, MarketType.CRYPTO, "ETH", AlertDirection.ABOVE,
                BigDecimal.valueOf(100), BigDecimal.valueOf(110), "image", "ETH name", "TRY");
        when(alertService.activeAlertsAfter(eq(MarketType.CRYPTO), eq(0L), anyInt())).thenReturn(List.of(bad, good));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC", "ETH"))))
                .thenReturn(Map.of("BTC", btc, "ETH", eth));
        doThrow(new RuntimeException("persist boom")).when(alertService).persist(bad);
        when(priceAlertMapper.toFiredPayload(good, eth, MarketType.CRYPTO)).thenReturn(goodPayload);
        when(dispatcher.dispatchBatched(any())).thenReturn(new BatchResult(1, 0));

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        // The bad row is swallowed; the good row still fans out.
        assertThat(fired).isEqualTo(1);
        ArgumentCaptor<List<NotificationRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatchBatched(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).userSub()).isEqualTo("user-2");
    }

    @Test
    void should_skipDispatch_when_snapshotMissing() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlertsAfter(eq(MarketType.CRYPTO), eq(0L), anyInt())).thenReturn(List.of(alert));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of());

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatchBatched(any());
        verify(alertService, never()).persist(any());
    }

    @Test
    void should_notFire_when_thresholdNotCrossed() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlertsAfter(eq(MarketType.CRYPTO), eq(0L), anyInt())).thenReturn(List.of(alert));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of("BTC", snapshot("BTC", BigDecimal.valueOf(99))));

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatchBatched(any());
    }

    @Test
    void should_scanEveryAlertAcrossKeysetPages_advancingCursorByLastId() {
        // Page size 2 forces two scan iterations over three alerts; the cursor must advance by the last id of
        // each page (0 -> 2) so the third alert is picked up, and the partial final page ends the loop.
        ReflectionTestUtils.setField(evaluator, "scanPageSize", 2);
        PriceAlert a1 = firingAlert(1L, "BTC");
        PriceAlert a2 = firingAlert(2L, "ETH");
        PriceAlert a3 = firingAlert(3L, "SOL");
        when(alertService.activeAlertsAfter(MarketType.CRYPTO, 0L, 2)).thenReturn(List.of(a1, a2));
        when(alertService.activeAlertsAfter(MarketType.CRYPTO, 2L, 2)).thenReturn(List.of(a3));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), any())).thenAnswer(inv -> {
            Set<String> codes = inv.getArgument(1);
            Map<String, AssetSnapshot> map = new HashMap<>();
            for (String code : codes) map.put(code, snapshot(code, BigDecimal.valueOf(150)));
            return map;
        });
        when(priceAlertMapper.toFiredPayload(any(), any(), eq(MarketType.CRYPTO))).thenReturn(
                new PriceAlertPayload(0L, MarketType.CRYPTO, "X", AlertDirection.ABOVE,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(150), "i", "n", "TRY"));
        when(dispatcher.dispatchBatched(any())).thenAnswer(inv ->
                new BatchResult(((List<?>) inv.getArgument(0)).size(), 0));

        int dispatched = evaluator.evaluate(MarketType.CRYPTO);

        // Both pages were queried with the advancing cursor, and every alert across both pages fired.
        verify(alertService).activeAlertsAfter(MarketType.CRYPTO, 0L, 2);
        verify(alertService).activeAlertsAfter(MarketType.CRYPTO, 2L, 2);
        verify(alertService).persist(a1);
        verify(alertService).persist(a2);
        verify(alertService).persist(a3);
        assertThat(dispatched).isEqualTo(3);
    }

    private PriceAlert firingAlert(long id, String code) {
        return PriceAlert.builder()
                .id(id).userSub("user-" + id).marketType(MarketType.CRYPTO).assetCode(code)
                .direction(AlertDirection.ABOVE).threshold(BigDecimal.valueOf(100)).currency("TRY").active(true).build();
    }
}
