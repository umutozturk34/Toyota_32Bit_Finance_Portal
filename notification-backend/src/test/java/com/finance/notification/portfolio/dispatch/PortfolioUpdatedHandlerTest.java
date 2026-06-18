package com.finance.notification.portfolio.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.PortfolioUpdatedPayload;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortfolioUpdatedHandlerTest {

    @Mock private Translator translator;

    private PortfolioUpdatedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PortfolioUpdatedHandler(translator);
        when(translator.translate(anyString(), any(Locale.class), org.mockito.Mockito.any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void type_isPortfolioUpdated() {
        assertThat(handler.type()).isEqualTo(NotificationType.PORTFOLIO_UPDATED);
    }

    @Test
    void render_raises_whenPayloadIsNotPortfolioUpdated() {
        SystemPayload wrong = new SystemPayload("t", "b", "admin");
        NotificationRequest req = NotificationRequest.of("u", wrong);

        assertThatThrownBy(() -> handler.render(req, Locale.ENGLISH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void render_choosesMorningTitle_whenSourceMorning() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                new BigDecimal("10000"), new BigDecimal("100"), new BigDecimal("1.0"), 1, List.of(), "morning");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification result = handler.render(req, Locale.ENGLISH);

        assertThat(result.title()).isEqualTo("notif.portfolioUpdated.titleMorning");
    }

    @Test
    void render_choosesEveningTitle_whenSourceEvening() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                new BigDecimal("10000"), new BigDecimal("100"), new BigDecimal("1.0"), 1, List.of(), "evening");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification result = handler.render(req, Locale.ENGLISH);

        assertThat(result.title()).isEqualTo("notif.portfolioUpdated.titleEvening");
    }

    @Test
    void render_choosesGenericTitle_whenSourceMissing() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                new BigDecimal("10000"), new BigDecimal("100"), new BigDecimal("1.0"), 1, List.of(), null);
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification result = handler.render(req, Locale.ENGLISH);

        assertThat(result.title()).isEqualTo("notif.portfolioUpdated.title");
    }

    @Test
    void render_usesSnapshotOnlyBody_whenTotalValueMissing() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                null, null, null, 1, List.of(), "morning");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification result = handler.render(req, Locale.ENGLISH);

        assertThat(result.body()).isEqualTo("notif.portfolioUpdated.bodySnapshotOnly");
    }

    @Test
    void render_usesValueOnlyBody_whenPnlMissing() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                new BigDecimal("12345.67"), null, null, 1, List.of(), "morning");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification result = handler.render(req, Locale.ENGLISH);

        assertThat(result.body()).isEqualTo("notif.portfolioUpdated.bodyValueOnly");
    }

    @Test
    void render_usesFullBody_whenPnlPresent() {
        PortfolioUpdatedPayload payload = new PortfolioUpdatedPayload(
                new BigDecimal("12345.67"), new BigDecimal("-100"), new BigDecimal("-1.5"), 1, List.of(), "morning");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification result = handler.render(req, Locale.ENGLISH);

        assertThat(result.body()).isEqualTo("notif.portfolioUpdated.bodyFull");
        assertThat(result.emailModel()).containsKey("totalValue");
        assertThat(result.emailModel()).containsKey("dailyPnl");
        assertThat(result.emailModel()).containsKey("dailyPnlPercent");
    }
}
