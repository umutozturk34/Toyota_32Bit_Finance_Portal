package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.macro.dto.response.MacroIndicatorPointResponse;
import com.finance.market.macro.dto.response.MacroIndicatorResponse;
import com.finance.market.macro.mapper.MacroIndicatorResponseMapper;
import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.service.MacroIndicatorQueryService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MacroIndicatorControllerTest {

    @Mock private MacroIndicatorQueryService queryService;
    @Mock private MacroIndicatorResponseMapper mapper;
    @Mock private Translator translator;
    @Mock private MacroIndicator indicator;
    @Mock private MacroIndicatorPoint point;

    private MacroIndicatorController controller;

    @BeforeEach
    void setUp() {
        com.finance.market.macro.config.MacroProperties macroProperties =
                new com.finance.market.macro.config.MacroProperties(
                        java.time.LocalDate.of(1995, 1, 1), 25, 1000, null, java.util.List.of());
        controller = new MacroIndicatorController(queryService, mapper, translator, macroProperties);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private MacroIndicatorResponse response(String code) {
        return new MacroIndicatorResponse(code, "Label", MacroCategory.RATES,
                MacroUnit.PERCENT, MacroFrequency.DAILY, "TRY",
                DepositMaturity.M1, false, new BigDecimal("12.5"), LocalDate.now());
    }

    @Test
    void shouldReturnProminentIndicators_whenProminentOnlyIsTrue() {
        List<MacroIndicator> models = List.of(indicator);
        List<MacroIndicatorResponse> responses = List.of(response("PROM"));
        when(queryService.listProminent()).thenReturn(models);
        when(mapper.toResponses(models)).thenReturn(responses);

        ApiResponse<List<MacroIndicatorResponse>> result = controller.list(null, true);

        assertThat(result.getData()).isEqualTo(responses);
        assertThat(result.getMessage()).isEqualTo("api.macro.retrieved");
        verify(queryService).listProminent();
        verify(queryService, never()).listAll();
        verify(queryService, never()).listByCategory(MacroCategory.RATES);
    }

    @Test
    void shouldReturnByCategory_whenCategoryProvidedAndNotProminentOnly() {
        List<MacroIndicator> models = List.of(indicator);
        List<MacroIndicatorResponse> responses = List.of(response("CAT"));
        when(queryService.listByCategory(MacroCategory.INFLATION)).thenReturn(models);
        when(mapper.toResponses(models)).thenReturn(responses);

        ApiResponse<List<MacroIndicatorResponse>> result = controller.list(MacroCategory.INFLATION, false);

        assertThat(result.getData()).isEqualTo(responses);
        verify(queryService).listByCategory(MacroCategory.INFLATION);
        verify(queryService, never()).listAll();
        verify(queryService, never()).listProminent();
    }

    @Test
    void shouldReturnAllIndicators_whenNoCategoryAndNotProminentOnly() {
        List<MacroIndicator> models = List.of(indicator);
        List<MacroIndicatorResponse> responses = List.of(response("ALL"));
        when(queryService.listAll()).thenReturn(models);
        when(mapper.toResponses(models)).thenReturn(responses);

        ApiResponse<List<MacroIndicatorResponse>> result = controller.list(null, false);

        assertThat(result.getData()).isEqualTo(responses);
        verify(queryService).listAll();
        verify(queryService, never()).listProminent();
    }

    @Test
    void shouldPreferProminent_whenBothCategoryAndProminentOnlyProvided() {
        List<MacroIndicator> models = List.of(indicator);
        when(queryService.listProminent()).thenReturn(models);
        when(mapper.toResponses(models)).thenReturn(List.of(response("P")));

        controller.list(MacroCategory.RATES, true);

        verify(queryService).listProminent();
        verify(queryService, never()).listByCategory(MacroCategory.RATES);
        verify(queryService, never()).listAll();
    }

    @Test
    void shouldUseProvidedRange_whenFromAndToProvided() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        List<MacroIndicatorPoint> points = List.of(point);
        List<MacroIndicatorPointResponse> pointResponses =
                List.of(new MacroIndicatorPointResponse(LocalDate.now(), new BigDecimal("1.23")));
        when(queryService.findByCode("CPI")).thenReturn(indicator);
        when(queryService.history(indicator, from, to)).thenReturn(points);
        when(mapper.toPointResponses(points)).thenReturn(pointResponses);

        ApiResponse<List<MacroIndicatorPointResponse>> result = controller.history("CPI", from, to);

        assertThat(result.getData()).isEqualTo(pointResponses);
        assertThat(result.getMessage()).isEqualTo("api.macro.historyRetrieved");
        verify(queryService).history(indicator, from, to);
    }

    @Test
    void shouldDefaultToFromMinus5Years_whenFromIsNull() {
        LocalDate to = LocalDate.of(2024, 12, 31);
        LocalDate expectedFrom = to.minusYears(5);
        when(queryService.findByCode("CPI")).thenReturn(indicator);
        when(queryService.history(indicator, expectedFrom, to)).thenReturn(List.of());
        when(mapper.toPointResponses(List.of())).thenReturn(List.of());

        controller.history("CPI", null, to);

        verify(queryService).history(indicator, expectedFrom, to);
    }

    @Test
    void shouldDefaultToToToday_whenToIsNull() {
        LocalDate from = LocalDate.of(2020, 1, 1);
        when(queryService.findByCode("CPI")).thenReturn(indicator);
        when(queryService.history(org.mockito.ArgumentMatchers.eq(indicator),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());
        when(mapper.toPointResponses(List.of())).thenReturn(List.of());

        controller.history("CPI", from, null);

        org.mockito.ArgumentCaptor<LocalDate> toCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(queryService).history(org.mockito.ArgumentMatchers.eq(indicator),
                org.mockito.ArgumentMatchers.eq(from), toCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    void shouldDefaultBothBounds_whenBothNull() {
        when(queryService.findByCode("CPI")).thenReturn(indicator);
        when(queryService.history(org.mockito.ArgumentMatchers.eq(indicator),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.any(LocalDate.class)))
                .thenReturn(List.of());
        when(mapper.toPointResponses(List.of())).thenReturn(List.of());

        controller.history("CPI", null, null);

        org.mockito.ArgumentCaptor<LocalDate> fromCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        org.mockito.ArgumentCaptor<LocalDate> toCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(queryService).history(org.mockito.ArgumentMatchers.eq(indicator),
                fromCaptor.capture(), toCaptor.capture());
        LocalDate today = LocalDate.now();
        assertThat(toCaptor.getValue()).isEqualTo(today);
        assertThat(fromCaptor.getValue()).isEqualTo(today.minusYears(5));
    }
}
