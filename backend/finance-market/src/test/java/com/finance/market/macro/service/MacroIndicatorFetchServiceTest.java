package com.finance.market.macro.service;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.macro.client.EvdsMacroClient;
import com.finance.market.macro.config.MacroProperties;
import com.finance.market.macro.dto.internal.MacroObservation;
import com.finance.market.macro.mapper.EvdsMacroMapper;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.repository.MacroIndicatorPointRepository;
import com.finance.market.macro.repository.MacroIndicatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorFetchServiceTest {

    @Mock private MacroIndicatorRepository indicatorRepository;
    @Mock private MacroIndicatorPointRepository pointRepository;
    @Mock private EvdsMacroClient client;
    @Mock private EvdsMacroMapper mapper;

    private MacroProperties properties;
    private MacroIndicatorFetchService service;

    @BeforeEach
    void setUp() {
        properties = new MacroProperties(LocalDate.of(2018, 1, 1), 25, 1000, null, null, List.of());
        service = new MacroIndicatorFetchService(indicatorRepository, pointRepository,
                client, mapper, properties);
    }

    @Test
    void should_returnZeroOutcome_when_noIndicatorsRegistered() {
        when(indicatorRepository.findAll()).thenReturn(List.of());

        MacroIndicatorFetchService.FetchOutcome outcome = service.refreshAll();

        assertThat(outcome.indicatorsTouched()).isZero();
        assertThat(outcome.pointsInserted()).isZero();
    }

    @Test
    void should_insertNewPoints_when_observationsAreFresh() {
        MacroIndicator indicator = buildIndicator("TP.RATE");
        EvdsDataResponse response = new EvdsDataResponse(0, List.of());
        when(indicatorRepository.findAll()).thenReturn(List.of(indicator));
        when(client.fetchSeriesBatched(any(), any(), any(), anyInt()))
                .thenReturn(List.of(response));
        when(mapper.extract(response, "TP.RATE")).thenReturn(List.of(
                new MacroObservation(LocalDate.of(2026, 5, 1), new BigDecimal("40.00")),
                new MacroObservation(LocalDate.of(2026, 5, 2), new BigDecimal("40.50"))));
        when(pointRepository.existsByIndicatorAndObservedAt(eq(indicator), any())).thenReturn(false);

        MacroIndicatorFetchService.FetchOutcome outcome = service.refreshAll();

        assertThat(outcome.indicatorsTouched()).isEqualTo(1);
        assertThat(outcome.pointsInserted()).isEqualTo(2);
        verify(pointRepository, org.mockito.Mockito.times(2)).save(any(MacroIndicatorPoint.class));
        assertThat(indicator.getLastDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        assertThat(indicator.getLastValue()).isEqualByComparingTo("40.50");
    }

    @Test
    void should_recordChangedCode_when_indicatorGainsNewPoint() {
        MacroIndicator indicator = buildIndicator("TP.RATE");
        EvdsDataResponse response = new EvdsDataResponse(0, List.of());
        when(indicatorRepository.findAll()).thenReturn(List.of(indicator));
        when(client.fetchSeriesBatched(any(), any(), any(), anyInt()))
                .thenReturn(List.of(response));
        when(mapper.extract(response, "TP.RATE")).thenReturn(List.of(
                new MacroObservation(LocalDate.of(2026, 5, 2), new BigDecimal("40.50"))));
        when(pointRepository.existsByIndicatorAndObservedAt(eq(indicator), any())).thenReturn(false);

        MacroIndicatorFetchService.FetchOutcome outcome = service.refreshAll();

        assertThat(outcome.changedCodes()).containsExactly("TP.RATE");
    }

    @Test
    void should_returnEmptyChangedCodes_when_allObservationsAreDuplicates() {
        MacroIndicator indicator = buildIndicator("TP.RATE");
        EvdsDataResponse response = new EvdsDataResponse(0, List.of());
        when(indicatorRepository.findAll()).thenReturn(List.of(indicator));
        when(client.fetchSeriesBatched(any(), any(), any(), anyInt()))
                .thenReturn(List.of(response));
        when(mapper.extract(response, "TP.RATE")).thenReturn(List.of(
                new MacroObservation(LocalDate.of(2026, 5, 1), new BigDecimal("40.00"))));
        when(pointRepository.existsByIndicatorAndObservedAt(eq(indicator), any())).thenReturn(true);

        MacroIndicatorFetchService.FetchOutcome outcome = service.refreshAll();

        assertThat(outcome.changedCodes()).isEmpty();
    }

    @Test
    void should_skipDuplicates_when_pointExistsForSameDate() {
        MacroIndicator indicator = buildIndicator("TP.RATE");
        EvdsDataResponse response = new EvdsDataResponse(0, List.of());
        when(indicatorRepository.findAll()).thenReturn(List.of(indicator));
        when(client.fetchSeriesBatched(any(), any(), any(), anyInt()))
                .thenReturn(List.of(response));
        when(mapper.extract(response, "TP.RATE")).thenReturn(List.of(
                new MacroObservation(LocalDate.of(2026, 5, 1), new BigDecimal("40.00"))));
        when(pointRepository.existsByIndicatorAndObservedAt(eq(indicator), any())).thenReturn(true);

        MacroIndicatorFetchService.FetchOutcome outcome = service.refreshAll();

        assertThat(outcome.pointsInserted()).isZero();
        verify(pointRepository, never()).save(any(MacroIndicatorPoint.class));
    }

    private MacroIndicator buildIndicator(String code) {
        Instrument instrument = Instrument.create(MarketType.MACRO_RATE, code);
        return MacroIndicator.builder()
                .instrument(instrument).code(code).label("test")
                .category(MacroCategory.RATES).unit(MacroUnit.PERCENT)
                .frequency(MacroFrequency.DAILY).prominent(true).build();
    }
}
