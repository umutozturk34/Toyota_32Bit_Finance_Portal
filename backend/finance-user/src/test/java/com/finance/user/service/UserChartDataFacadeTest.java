package com.finance.user.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.model.TrackedAssetType;
import com.finance.user.config.ChartDefaultsProperties;
import com.finance.user.config.ChartDefaultsProperties.AssetTypeRules;
import com.finance.user.config.ChartDefaultsProperties.Limits;
import com.finance.user.dto.UserChartBundleResponse;
import com.finance.user.dto.UserChartDrawingResponse;
import com.finance.user.dto.UserChartPreferenceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserChartDataFacadeTest {

    private static final String USER = "kc-user-123";
    private static final String CODE = "BTC";
    private static final TrackedAssetType TYPE = TrackedAssetType.CRYPTO;

    @Mock private UserChartPreferenceService preferenceService;
    @Mock private UserChartDrawingService drawingService;

    private UserChartDataFacade facade;

    @BeforeEach
    void setUp() {
        AssetTypeRules cryptoRules = new AssetTypeRules(
                Set.of("candle", "line"),
                true,
                false,
                false,
                Set.of("ema", "sma"),
                Set.of("retracement"),
                Set.of("trendline", "rectangle")
        );
        Limits limits = new Limits(10, 5, 3);
        ChartDefaultsProperties chartDefaults = new ChartDefaultsProperties(
                null, null, limits, Map.of(TYPE, cryptoRules));
        facade = new UserChartDataFacade(preferenceService, drawingService, chartDefaults);
    }

    @Test
    void getBundle_returnsWrappedPrefsAndDrawings_whenServicesReturnData() {
        UserChartPreferenceResponse prefs = new UserChartPreferenceResponse(
                JsonNodeFactory.instance.objectNode(), Instant.now());
        UserChartDrawingResponse drawings = new UserChartDrawingResponse(
                JsonNodeFactory.instance.arrayNode(), Instant.now());
        when(preferenceService.getOrDefault(USER, TYPE, CODE)).thenReturn(prefs);
        when(drawingService.getOrDefault(USER, TYPE, CODE)).thenReturn(drawings);

        UserChartBundleResponse bundle = facade.getBundle(USER, TYPE, CODE, "1D");

        assertThat(bundle.preferences()).isSameAs(prefs);
        assertThat(bundle.drawings()).isSameAs(drawings);
    }

    @Test
    void upsertPreferences_delegates_whenNoRulesForType() {
        ChartDefaultsProperties noRules = new ChartDefaultsProperties(
                null, null, new Limits(10, 5, 3), Map.of());
        UserChartDataFacade noRulesFacade = new UserChartDataFacade(preferenceService, drawingService, noRules);
        ObjectNode config = JsonNodeFactory.instance.objectNode().put("chartType", "anything");
        UserChartPreferenceResponse expected = new UserChartPreferenceResponse(config, Instant.now());
        when(preferenceService.upsert(USER, TYPE, CODE, config)).thenReturn(expected);

        UserChartPreferenceResponse result = noRulesFacade.upsertPreferences(USER, TYPE, CODE, config);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertPreferences_delegates_whenConfigIsNull() {
        UserChartPreferenceResponse expected = new UserChartPreferenceResponse(
                JsonNodeFactory.instance.objectNode(), Instant.now());
        when(preferenceService.upsert(USER, TYPE, CODE, null)).thenReturn(expected);

        UserChartPreferenceResponse result = facade.upsertPreferences(USER, TYPE, CODE, null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertPreferences_throwsChartTypeNotAllowed_whenChartTypeNotInAllowedSet() {
        ObjectNode config = JsonNodeFactory.instance.objectNode().put("chartType", "heikinashi");

        assertThatThrownBy(() -> facade.upsertPreferences(USER, TYPE, CODE, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.chart.chartTypeNotAllowed");
        verify(preferenceService, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void upsertPreferences_passes_whenChartTypeIsAllowed() {
        ObjectNode config = JsonNodeFactory.instance.objectNode().put("chartType", "candle");
        UserChartPreferenceResponse expected = new UserChartPreferenceResponse(config, Instant.now());
        when(preferenceService.upsert(USER, TYPE, CODE, config)).thenReturn(expected);

        UserChartPreferenceResponse result = facade.upsertPreferences(USER, TYPE, CODE, config);

        assertThat(result).isSameAs(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "showInvestorCount, error.chart.investorCountNotAllowed",
            "showPortfolioSize, error.chart.portfolioSizeNotAllowed"
    })
    void upsertPreferences_throwsFlagNotAllowed_whenFlagDisallowedAndTrue(String flagName, String errorKey) {
        ObjectNode config = JsonNodeFactory.instance.objectNode().put(flagName, true);

        assertThatThrownBy(() -> facade.upsertPreferences(USER, TYPE, CODE, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(errorKey);
    }

    @Test
    void upsertPreferences_passes_whenAllowedFlagIsTrue() {
        ObjectNode config = JsonNodeFactory.instance.objectNode().put("showVolume", true);
        UserChartPreferenceResponse expected = new UserChartPreferenceResponse(config, Instant.now());
        when(preferenceService.upsert(USER, TYPE, CODE, config)).thenReturn(expected);

        UserChartPreferenceResponse result = facade.upsertPreferences(USER, TYPE, CODE, config);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertPreferences_passes_whenDisallowedFlagIsFalse() {
        ObjectNode config = JsonNodeFactory.instance.objectNode().put("showInvestorCount", false);
        UserChartPreferenceResponse expected = new UserChartPreferenceResponse(config, Instant.now());
        when(preferenceService.upsert(USER, TYPE, CODE, config)).thenReturn(expected);

        UserChartPreferenceResponse result = facade.upsertPreferences(USER, TYPE, CODE, config);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertPreferences_throwsIndicatorTypeNotAllowed_whenIndicatorNotInAllowedSet() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        ArrayNode indicators = config.putArray("indicators");
        indicators.addObject().put("type", "macd");

        assertThatThrownBy(() -> facade.upsertPreferences(USER, TYPE, CODE, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.chart.indicatorTypeNotAllowed");
    }

    @Test
    void upsertPreferences_passes_whenIndicatorIsAllowed() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.putArray("indicators").addObject().put("type", "ema");
        UserChartPreferenceResponse expected = new UserChartPreferenceResponse(config, Instant.now());
        when(preferenceService.upsert(eq(USER), eq(TYPE), eq(CODE), any())).thenReturn(expected);

        UserChartPreferenceResponse result = facade.upsertPreferences(USER, TYPE, CODE, config);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertPreferences_throwsFibToolTypeNotAllowed_whenFibToolNotInAllowedSet() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        config.putArray("fibTools").addObject().put("type", "extension");

        assertThatThrownBy(() -> facade.upsertPreferences(USER, TYPE, CODE, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.chart.fibToolTypeNotAllowed");
    }

    @Test
    void upsertPreferences_throwsIndicatorLimit_whenTooManyIndicators() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        ArrayNode indicators = config.putArray("indicators");
        for (int i = 0; i < 6; i++) indicators.addObject().put("type", "ema");

        assertThatThrownBy(() -> facade.upsertPreferences(USER, TYPE, CODE, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.chart.indicatorLimit");
    }

    @Test
    void upsertPreferences_throwsFibToolLimit_whenTooManyFibTools() {
        ObjectNode config = JsonNodeFactory.instance.objectNode();
        ArrayNode fibs = config.putArray("fibTools");
        for (int i = 0; i < 4; i++) fibs.addObject().put("type", "retracement");

        assertThatThrownBy(() -> facade.upsertPreferences(USER, TYPE, CODE, config))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.chart.fibToolLimit");
    }

    @Test
    void upsertDrawings_delegates_whenNoRulesForType() {
        ChartDefaultsProperties noRules = new ChartDefaultsProperties(
                null, null, new Limits(10, 5, 3), Map.of());
        UserChartDataFacade noRulesFacade = new UserChartDataFacade(preferenceService, drawingService, noRules);
        ArrayNode drawings = JsonNodeFactory.instance.arrayNode();
        UserChartDrawingResponse expected = new UserChartDrawingResponse(drawings, Instant.now());
        when(drawingService.upsert(USER, TYPE, CODE, drawings)).thenReturn(expected);

        UserChartDrawingResponse result = noRulesFacade.upsertDrawings(USER, TYPE, CODE, drawings);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertDrawings_delegates_whenDrawingsIsNull() {
        UserChartDrawingResponse expected = new UserChartDrawingResponse(
                JsonNodeFactory.instance.arrayNode(), Instant.now());
        when(drawingService.upsert(USER, TYPE, CODE, null)).thenReturn(expected);

        UserChartDrawingResponse result = facade.upsertDrawings(USER, TYPE, CODE, null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertDrawings_throwsDrawingTypeNotAllowed_whenDrawingTypeNotInAllowedSet() {
        ArrayNode drawings = JsonNodeFactory.instance.arrayNode();
        drawings.addObject().put("type", "circle");

        assertThatThrownBy(() -> facade.upsertDrawings(USER, TYPE, CODE, drawings))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.chart.drawingTypeNotAllowed");
        verify(drawingService, never()).upsert(any(), any(), any(), any());
    }

    @Test
    void upsertDrawings_skipsBlankType_whenDrawingTypeIsEmpty() {
        ArrayNode drawings = JsonNodeFactory.instance.arrayNode();
        drawings.addObject().put("type", "");
        UserChartDrawingResponse expected = new UserChartDrawingResponse(drawings, Instant.now());
        when(drawingService.upsert(USER, TYPE, CODE, drawings)).thenReturn(expected);

        UserChartDrawingResponse result = facade.upsertDrawings(USER, TYPE, CODE, drawings);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertDrawings_passes_whenAllDrawingTypesAreAllowed() {
        ArrayNode drawings = JsonNodeFactory.instance.arrayNode();
        drawings.addObject().put("type", "trendline");
        drawings.addObject().put("type", "rectangle");
        UserChartDrawingResponse expected = new UserChartDrawingResponse(drawings, Instant.now());
        when(drawingService.upsert(USER, TYPE, CODE, drawings)).thenReturn(expected);

        UserChartDrawingResponse result = facade.upsertDrawings(USER, TYPE, CODE, drawings);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void upsertDrawings_throwsDrawingLimit_whenTooManyDrawings() {
        ArrayNode drawings = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 11; i++) drawings.addObject().put("type", "trendline");

        assertThatThrownBy(() -> facade.upsertDrawings(USER, TYPE, CODE, drawings))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.chart.drawingLimit");
    }

    @Test
    void upsertDrawings_passesUnderLimit_whenDrawingCountAtMax() {
        ArrayNode drawings = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 10; i++) drawings.addObject().put("type", "trendline");
        UserChartDrawingResponse expected = new UserChartDrawingResponse(drawings, Instant.now());
        when(drawingService.upsert(USER, TYPE, CODE, drawings)).thenReturn(expected);

        UserChartDrawingResponse result = facade.upsertDrawings(USER, TYPE, CODE, drawings);

        assertThat(result).isSameAs(expected);
    }
}
