package com.finance.notification.alert.service;

import com.finance.common.cache.AssetSnapshotCache;
import com.finance.common.dto.internal.AssetSnapshot;
import com.finance.common.model.MarketType;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.core.dispatch.NotificationDispatcher;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertEvaluatorTest {

    @Mock private PriceAlertService alertService;
    @Mock private NotificationDispatcher dispatcher;
    @Mock private AssetSnapshotCache assetSnapshotCache;

    @InjectMocks
    private PriceAlertEvaluator evaluator;

    private PriceAlert alertFor(String code, AlertDirection dir, BigDecimal thr, BigDecimal ref) {
        return PriceAlert.builder()
                .id(1L).userSub("user-1").marketType(MarketType.CRYPTO).assetCode(code)
                .direction(dir).threshold(thr).referencePrice(ref).currency("TRY").active(true).build();
    }

    private AssetSnapshot snapshot(String code, BigDecimal price) {
        return new AssetSnapshot(code, code, "https://i.example/" + code + ".png", price);
    }

    @Test
    void evaluate_skipsWhenNoActiveAlerts() {
        when(alertService.activeAlerts(MarketType.CRYPTO)).thenReturn(List.of());

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(assetSnapshotCache, never()).findByCodes(any(), any());
    }

    @Test
    void evaluate_firesAndPersistsAndDispatchesWhenAboveTriggered() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlerts(MarketType.CRYPTO)).thenReturn(List.of(alert));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of("BTC", snapshot("BTC", BigDecimal.valueOf(105))));

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(1);
        assertThat(alert.getTriggeredAt()).isNotNull();
        assertThat(alert.isActive()).isFalse();
        verify(alertService).persist(alert);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.type()).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
        assertThat(request.userSub()).isEqualTo("user-1");
        assertThat(request.payload()).isInstanceOf(PriceAlertPayload.class);
        PriceAlertPayload payload = (PriceAlertPayload) request.payload();
        assertThat(payload.assetCode()).isEqualTo("BTC");
        assertThat(payload.currentPrice()).isEqualByComparingTo(BigDecimal.valueOf(105));
        assertThat(payload.image()).isEqualTo("https://i.example/BTC.png");
    }

    @Test
    void evaluate_skipsWhenSnapshotMissing() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlerts(MarketType.CRYPTO)).thenReturn(List.of(alert));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of());

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(alertService, never()).persist(any());
    }

    @Test
    void evaluate_doesNotFireWhenThresholdNotCrossed() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlerts(MarketType.CRYPTO)).thenReturn(List.of(alert));
        when(assetSnapshotCache.findByCodes(eq(MarketType.CRYPTO), eq(Set.of("BTC"))))
                .thenReturn(Map.of("BTC", snapshot("BTC", BigDecimal.valueOf(99))));

        int fired = evaluator.evaluate(MarketType.CRYPTO);

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
    }
}
