package com.finance.notification.alert.service;

import com.finance.common.model.MarketType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceAlertEvaluatorTest {

    @Mock private PriceAlertService alertService;
    @Mock private NotificationDispatcher dispatcher;

    @InjectMocks
    private PriceAlertEvaluator evaluator;

    private PriceAlert alertFor(String code, AlertDirection dir, BigDecimal thr, BigDecimal ref) {
        return PriceAlert.builder()
                .id(1L).userSub("user-1").marketType(MarketType.CRYPTO).assetCode(code)
                .direction(dir).threshold(thr).referencePrice(ref).currency("TRY").active(true).build();
    }

    @Test
    void evaluate_skipsWhenPricesEmpty() {
        int fired = evaluator.evaluate(MarketType.CRYPTO, Map.of());

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void evaluate_firesAndPersistsAndDispatchesWhenAboveTriggered() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlerts(MarketType.CRYPTO)).thenReturn(List.of(alert));

        int fired = evaluator.evaluate(MarketType.CRYPTO, Map.of("BTC", BigDecimal.valueOf(105)));

        assertThat(fired).isEqualTo(1);
        assertThat(alert.getTriggeredAt()).isNotNull();
        assertThat(alert.isActive()).isFalse();
        verify(alertService).persist(alert);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.type()).isEqualTo(NotificationType.PRICE_ALERT_FIRED);
        assertThat(request.userSub()).isEqualTo("user-1");
        assertThat(request.data()).containsEntry("assetCode", "BTC");
        assertThat(request.data()).containsEntry("currentPrice", BigDecimal.valueOf(105));
    }

    @Test
    void evaluate_skipsWhenAssetCodeMissingFromPrices() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlerts(MarketType.CRYPTO)).thenReturn(List.of(alert));

        int fired = evaluator.evaluate(MarketType.CRYPTO, Map.of("ETH", BigDecimal.valueOf(200)));

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
        verify(alertService, never()).persist(any());
    }

    @Test
    void evaluate_doesNotFireWhenThresholdNotCrossed() {
        PriceAlert alert = alertFor("BTC", AlertDirection.ABOVE, BigDecimal.valueOf(100), null);
        when(alertService.activeAlerts(MarketType.CRYPTO)).thenReturn(List.of(alert));

        int fired = evaluator.evaluate(MarketType.CRYPTO, Map.of("BTC", BigDecimal.valueOf(99)));

        assertThat(fired).isEqualTo(0);
        verify(dispatcher, never()).dispatch(any());
    }
}
