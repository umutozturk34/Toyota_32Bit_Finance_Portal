package com.finance.market.macro.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorQueryServiceTest {

    @Mock private MacroIndicatorRepository indicatorRepository;
    @Mock private MacroIndicatorPointRepository pointRepository;

    private MacroIndicatorQueryService service;

    @BeforeEach
    void setUp() {
        service = new MacroIndicatorQueryService(indicatorRepository, pointRepository);
    }

    @Test
    void should_returnAllIndicators_when_listingEverything() {
        MacroIndicator indicator = buildIndicator();
        when(indicatorRepository.findAll()).thenReturn(List.of(indicator));

        List<MacroIndicator> result = service.listAll();

        assertThat(result).containsExactly(indicator);
    }

    @Test
    void should_returnIndicatorsForCategory_when_filteringByCategory() {
        MacroIndicator indicator = buildIndicator();
        when(indicatorRepository.findByCategory(MacroCategory.RATES)).thenReturn(List.of(indicator));

        List<MacroIndicator> result = service.listByCategory(MacroCategory.RATES);

        assertThat(result).containsExactly(indicator);
    }

    @Test
    void should_returnProminentIndicators_when_listingForDashboard() {
        MacroIndicator indicator = buildIndicator();
        when(indicatorRepository.findByProminentTrueOrderByCategoryAsc()).thenReturn(List.of(indicator));

        List<MacroIndicator> result = service.listProminent();

        assertThat(result).containsExactly(indicator);
    }

    @Test
    void should_returnIndicator_when_lookingUpByExistingCode() {
        MacroIndicator indicator = buildIndicator();
        when(indicatorRepository.findByCode("TP.RATE")).thenReturn(Optional.of(indicator));

        MacroIndicator result = service.findByCode("TP.RATE");

        assertThat(result).isSameAs(indicator);
    }

    @Test
    void should_throwResourceNotFound_when_codeDoesNotExist() {
        when(indicatorRepository.findByCode("TP.MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("TP.MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_delegateHistoryQuery_when_rangeProvided() {
        MacroIndicator indicator = buildIndicator();
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2026, 5, 1);
        MacroIndicatorPoint point = MacroIndicatorPoint.builder()
                .indicator(indicator).observedAt(from).value(BigDecimal.ONE).build();
        when(pointRepository.findByIndicatorAndObservedAtBetweenOrderByObservedAtAsc(indicator, from, to))
                .thenReturn(List.of(point));

        List<MacroIndicatorPoint> result = service.history(indicator, from, to);

        assertThat(result).containsExactly(point);
    }

    @Test
    void should_resolveByRawCode_when_publicIdHasEvdsPrefix() {
        MacroIndicator indicator = buildIndicator();
        when(indicatorRepository.findByCode("TP.RATE")).thenReturn(Optional.of(indicator));

        MacroIndicator result = service.findByPublicId("TP.RATE");

        assertThat(result).isSameAs(indicator);
    }

    @Test
    void should_resolveBySlug_when_publicIdIsSlug() {
        MacroIndicator indicator = buildLabeledIndicator("Politika Faizi");
        when(indicatorRepository.findAll()).thenReturn(List.of(buildLabeledIndicator(null), indicator));

        MacroIndicator result = service.findByPublicId("politika-faizi");

        assertThat(result).isSameAs(indicator);
    }

    @Test
    void should_throwResourceNotFound_when_slugMatchesNoIndicator() {
        when(indicatorRepository.findAll()).thenReturn(List.of(buildLabeledIndicator("Politika Faizi")));

        assertThatThrownBy(() -> service.findByPublicId("does-not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private MacroIndicator buildLabeledIndicator(String label) {
        Instrument instrument = Instrument.create(MarketType.MACRO_RATE, "TP.RATE");
        return MacroIndicator.builder()
                .instrument(instrument).code("TP.RATE").label(label)
                .category(MacroCategory.RATES).unit(MacroUnit.PERCENT)
                .frequency(MacroFrequency.DAILY).prominent(true).build();
    }

    private MacroIndicator buildIndicator() {
        Instrument instrument = Instrument.create(MarketType.MACRO_RATE, "TP.RATE");
        return MacroIndicator.builder()
                .instrument(instrument).code("TP.RATE").label("test")
                .category(MacroCategory.RATES).unit(MacroUnit.PERCENT)
                .frequency(MacroFrequency.DAILY).prominent(true).build();
    }
}
