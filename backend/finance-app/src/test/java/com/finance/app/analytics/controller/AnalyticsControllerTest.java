package com.finance.app.analytics.controller;

import com.finance.app.analytics.dto.AnalyticsInstrument;
import com.finance.app.analytics.dto.AnalyticsInstrumentType;
import com.finance.app.analytics.dto.HistoryPoint;
import com.finance.app.analytics.dto.request.ScenarioRequest;
import com.finance.app.analytics.dto.response.InflationBeaterResponse;
import com.finance.app.analytics.dto.response.ScenarioResponse;
import com.finance.app.analytics.service.InflationBeaterService;
import com.finance.app.analytics.service.PortfolioSeriesProvider;
import com.finance.app.analytics.service.ScenarioService;
import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.common.model.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsControllerTest {

    @Mock private ScenarioService scenarioService;
    @Mock private InflationBeaterService inflationBeaterService;
    @Mock private PortfolioSeriesProvider portfolioSeriesProvider;
    @Mock private Translator translator;

    @InjectMocks
    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        when(translator.translate(anyString())).thenAnswer(inv -> "tr:" + inv.getArgument(0));
    }

    @Test
    void shouldDelegateToScenarioService_whenSimulateInvoked() {
        ScenarioRequest request = new ScenarioRequest(
                new BigDecimal("10000"),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                List.of(new AnalyticsInstrument(AnalyticsInstrumentType.SPOT, "THYAO.IS")));
        ScenarioResponse expected = new ScenarioResponse(
                new BigDecimal("10000"), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31),
                new BigDecimal("12"), null, List.of());
        when(scenarioService.simulate(request)).thenReturn(expected);

        ApiResponse<ScenarioResponse> response = controller.simulate(request);

        assertThat(response.getData()).isSameAs(expected);
        assertThat(response.getMessage()).isEqualTo("tr:api.analytics.scenarioComputed");
        assertThat(response.isSuccess()).isTrue();
        verify(scenarioService).simulate(request);
    }

    @Test
    void shouldDelegateToInflationBeaterService_whenInflationBeatersInvoked() {
        InflationBeaterResponse expected = new InflationBeaterResponse(
                LocalDate.now().minusYears(1), LocalDate.now(),
                "TP.TUFE1YI.T1", "TÜFE", new BigDecimal("30"), 1, 2,
                Currency.TRY, List.of());
        when(inflationBeaterService.rank("1Y", "TP.TUFE1YI.T1")).thenReturn(expected);

        ApiResponse<InflationBeaterResponse> response = controller.inflationBeaters("1Y", "TP.TUFE1YI.T1");

        assertThat(response.getData()).isSameAs(expected);
        assertThat(response.getMessage()).isEqualTo("tr:api.analytics.inflationBeatersComputed");
        verify(inflationBeaterService).rank("1Y", "TP.TUFE1YI.T1");
    }

    @Test
    void shouldPassNullBenchmark_whenBenchmarkOmitted() {
        InflationBeaterResponse expected = new InflationBeaterResponse(
                LocalDate.now().minusYears(1), LocalDate.now(),
                "TP.TUFE1YI.T1", "TÜFE", BigDecimal.ZERO, 0, 0,
                Currency.TRY, List.of());
        when(inflationBeaterService.rank("1M", null)).thenReturn(expected);

        ApiResponse<InflationBeaterResponse> response = controller.inflationBeaters("1M", null);

        assertThat(response.isSuccess()).isTrue();
        verify(inflationBeaterService).rank("1M", null);
    }

    @Test
    void shouldDelegateToPortfolioSeriesProvider_whenPortfolioSeriesInvoked() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-42")
                .claim("sub", "user-42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 6, 1);
        List<HistoryPoint> expected = List.of(
                new HistoryPoint(LocalDate.of(2024, 3, 1), new BigDecimal("12345")));
        when(portfolioSeriesProvider.dailyValueSeries(7L, "user-42", from, to)).thenReturn(expected);

        ApiResponse<List<HistoryPoint>> response = controller.portfolioSeries(jwt, 7L, from, to);

        assertThat(response.getData()).isEqualTo(expected);
        assertThat(response.getMessage()).isEqualTo("tr:api.analytics.portfolioSeriesRetrieved");
        verify(portfolioSeriesProvider).dailyValueSeries(7L, "user-42", from, to);
    }
}
