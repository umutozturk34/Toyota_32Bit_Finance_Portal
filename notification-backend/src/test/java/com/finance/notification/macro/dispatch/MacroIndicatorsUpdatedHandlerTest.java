package com.finance.notification.macro.dispatch;

import com.finance.common.i18n.Translator;
import com.finance.notification.core.dispatch.NotificationRequest;
import com.finance.notification.core.dispatch.RenderedNotification;
import com.finance.notification.core.dispatch.payload.MacroIndicatorsUpdatedPayload;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.macro.MacroIndicatorChangeReader.IndicatorChange;
import com.finance.notification.macro.MacroIndicatorChangeReader.IndicatorChange.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MacroIndicatorsUpdatedHandlerTest {

    @Mock private Translator translator;

    private MacroIndicatorsUpdatedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MacroIndicatorsUpdatedHandler(translator);
        when(translator.translate(anyString(), any(Locale.class), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(translator.translate(anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void type_isMacroIndicatorsUpdated() {
        assertThat(handler.type()).isEqualTo(NotificationType.MACRO_INDICATORS_UPDATED);
    }

    @Test
    void render_raises_whenPayloadIsWrongType() {
        SystemPayload wrong = new SystemPayload("t", "b", "admin");
        NotificationRequest req = NotificationRequest.of("u", wrong);

        assertThatThrownBy(() -> handler.render(req, Locale.ENGLISH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void render_buildsOneRowPerChange_withArrowAndPositiveDelta_forUpDirection() {
        IndicatorChange up = new IndicatorChange("TP.RATE", "CPI", "INFLATION", "PERCENT",
                null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("40.50"),
                LocalDate.of(2026, 5, 19), new BigDecimal("40.00"),
                new BigDecimal("0.50"), new BigDecimal("1.25"), Direction.UP);
        MacroIndicatorsUpdatedPayload payload = new MacroIndicatorsUpdatedPayload(List.of(up), "scheduled-macro-daily");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification rendered = handler.render(req, Locale.ENGLISH);

        assertThat(rendered.emailTemplate()).isEqualTo("macro-indicators-updated");
        assertThat(rendered.emailModel()).containsKey("rows");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rendered.emailModel().get("rows");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("arrow")).isEqualTo("▲");
        assertThat(row.get("direction")).isEqualTo("UP");
        assertThat((String) row.get("delta")).startsWith("+");
        assertThat((String) row.get("deltaPercent")).startsWith("+").endsWith("%");
    }

    @Test
    void render_marksDownArrow_andNegativeSign_forDownDirection() {
        IndicatorChange down = new IndicatorChange("TP.USD", "USD/TRY", "RATES", "NUMBER",
                "USD", null,
                LocalDate.of(2026, 5, 20), new BigDecimal("30.0000"),
                LocalDate.of(2026, 5, 19), new BigDecimal("31.0000"),
                new BigDecimal("-1.0000"), new BigDecimal("-3.225806"), Direction.DOWN);
        MacroIndicatorsUpdatedPayload payload = new MacroIndicatorsUpdatedPayload(List.of(down), "admin");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification rendered = handler.render(req, Locale.ENGLISH);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rendered.emailModel().get("rows");
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("arrow")).isEqualTo("▼");
        assertThat(row.get("direction")).isEqualTo("DOWN");
        assertThat((String) row.get("delta")).doesNotStartWith("+");
    }

    @Test
    void render_passesChangedCountAndUpDownCountersToModel() {
        IndicatorChange first = new IndicatorChange("A", "A", "INFLATION", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("1"), LocalDate.of(2026, 5, 19), new BigDecimal("0"),
                new BigDecimal("1"), new BigDecimal("100"), Direction.UP);
        IndicatorChange second = new IndicatorChange("B", "B", "RATES", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("2"), LocalDate.of(2026, 5, 19), new BigDecimal("3"),
                new BigDecimal("-1"), new BigDecimal("-33.33"), Direction.DOWN);
        MacroIndicatorsUpdatedPayload payload = new MacroIndicatorsUpdatedPayload(List.of(first, second), "scheduled-macro-daily");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification rendered = handler.render(req, Locale.ENGLISH);

        assertThat(rendered.emailModel().get("changedCount")).isEqualTo(2);
        assertThat(rendered.emailModel().get("upCount")).isEqualTo(1);
        assertThat(rendered.emailModel().get("downCount")).isEqualTo(1);
    }

    @Test
    void render_picksLargestAbsoluteDeltaAsHero() {
        IndicatorChange small = new IndicatorChange("S", "Small", "INFLATION", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("1.05"), LocalDate.of(2026, 5, 19), new BigDecimal("1.00"),
                new BigDecimal("0.05"), new BigDecimal("5"), Direction.UP);
        IndicatorChange huge = new IndicatorChange("H", "Huge", "RATES", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("8"), LocalDate.of(2026, 5, 19), new BigDecimal("10"),
                new BigDecimal("-2"), new BigDecimal("-20"), Direction.DOWN);
        MacroIndicatorsUpdatedPayload payload = new MacroIndicatorsUpdatedPayload(List.of(small, huge), "scheduled-macro-daily");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification rendered = handler.render(req, Locale.ENGLISH);

        assertThat(rendered.emailModel().get("hasHero")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> hero = (Map<String, Object>) rendered.emailModel().get("hero");
        assertThat(hero.get("code")).isEqualTo("H");
    }

    @Test
    void render_assignsCategoryAccent_byCategoryAndCurrency() {
        IndicatorChange inflation = new IndicatorChange("I", "I", "INFLATION", "PERCENT", null, null,
                LocalDate.of(2026, 5, 20), new BigDecimal("2"), LocalDate.of(2026, 5, 19), new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("100"), Direction.UP);
        IndicatorChange depositUsd = new IndicatorChange("D", "D", "DEPOSIT", "PERCENT", "USD", "M3",
                LocalDate.of(2026, 5, 20), new BigDecimal("3"), LocalDate.of(2026, 5, 19), new BigDecimal("2"),
                new BigDecimal("1"), new BigDecimal("50"), Direction.UP);
        MacroIndicatorsUpdatedPayload payload = new MacroIndicatorsUpdatedPayload(List.of(inflation, depositUsd), "scheduled-macro-daily");
        NotificationRequest req = NotificationRequest.of("u", payload);

        RenderedNotification rendered = handler.render(req, Locale.ENGLISH);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rendered.emailModel().get("rows");
        Map<String, Object> inflationRow = rows.stream().filter(r -> "I".equals(r.get("code"))).findFirst().orElseThrow();
        Map<String, Object> depositRow = rows.stream().filter(r -> "D".equals(r.get("code"))).findFirst().orElseThrow();
        assertThat(inflationRow.get("accent")).isEqualTo("#f59e0b");
        assertThat(depositRow.get("accent")).isEqualTo("#06b6d4");
    }
}
