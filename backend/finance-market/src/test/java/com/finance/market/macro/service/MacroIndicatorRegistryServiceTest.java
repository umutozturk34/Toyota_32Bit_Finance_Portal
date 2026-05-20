package com.finance.market.macro.service;

import com.finance.common.model.Instrument;
import com.finance.common.model.MarketType;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.macro.config.MacroProperties;
import com.finance.market.macro.config.MacroProperties.IndicatorDefinition;
import com.finance.market.macro.model.DepositMaturity;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroFrequency;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroUnit;
import com.finance.market.macro.repository.MacroIndicatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorRegistryServiceTest {

    @Mock private MacroIndicatorRepository macroRepository;
    @Mock private AssetRegistryService instrumentRegistry;

    private MacroProperties properties;
    private MacroIndicatorRegistryService service;

    @BeforeEach
    void setUp() {
        IndicatorDefinition definition = new IndicatorDefinition(
                "TP.RATE", "policyRate", MacroCategory.RATES, MacroUnit.PERCENT,
                MacroFrequency.DAILY, null, null, true);
        properties = new MacroProperties(java.time.LocalDate.of(2024, 1, 1), 10, 1000, List.of(definition));
        service = new MacroIndicatorRegistryService(macroRepository, instrumentRegistry, properties);
    }

    @Test
    void should_createAndPersistNewIndicator_when_codeNotYetRegistered() {
        Instrument instrument = Instrument.create(MarketType.MACRO_RATE, "TP.RATE");
        when(macroRepository.findByCode("TP.RATE")).thenReturn(Optional.empty());
        when(instrumentRegistry.upsert(MarketType.MACRO_RATE, "TP.RATE")).thenReturn(instrument);
        when(macroRepository.save(any(MacroIndicator.class))).thenAnswer(inv -> inv.getArgument(0));

        List<MacroIndicator> created = service.synchronizeFromConfig();

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getCode()).isEqualTo("TP.RATE");
        assertThat(created.get(0).getLabel()).isEqualTo("policyRate");
        verify(macroRepository).save(any(MacroIndicator.class));
    }

    @Test
    void should_updateExistingIndicator_when_codeAlreadyRegistered() {
        Instrument instrument = Instrument.create(MarketType.MACRO_RATE, "TP.RATE");
        MacroIndicator existing = MacroIndicator.builder()
                .instrument(instrument).code("TP.RATE").label("oldLabel")
                .category(MacroCategory.RATES).unit(MacroUnit.PERCENT)
                .frequency(MacroFrequency.WEEKLY).prominent(false).build();
        when(macroRepository.findByCode("TP.RATE")).thenReturn(Optional.of(existing));

        service.synchronizeFromConfig();

        assertThat(existing.getLabel()).isEqualTo("policyRate");
        assertThat(existing.getFrequency()).isEqualTo(MacroFrequency.DAILY);
        assertThat(existing.isProminent()).isTrue();
    }

    @Test
    void should_returnEmptyList_when_propertiesHaveNoIndicators() {
        MacroProperties empty = new MacroProperties(java.time.LocalDate.now(), 1, 1000, List.of());
        MacroIndicatorRegistryService emptyService =
                new MacroIndicatorRegistryService(macroRepository, instrumentRegistry, empty);

        List<MacroIndicator> result = emptyService.synchronizeFromConfig();

        assertThat(result).isEmpty();
    }

    @Test
    void should_assignCorrectInstrumentType_when_categoryIsDeposit() {
        MacroProperties depositProps = new MacroProperties(java.time.LocalDate.now(), 1, 1000, List.of(
                new IndicatorDefinition("TP.DEPOSIT", "depositTry", MacroCategory.DEPOSIT,
                        MacroUnit.PERCENT, MacroFrequency.WEEKLY, "TRY", DepositMaturity.TOTAL, true)));
        MacroIndicatorRegistryService depositService =
                new MacroIndicatorRegistryService(macroRepository, instrumentRegistry, depositProps);
        when(macroRepository.findByCode("TP.DEPOSIT")).thenReturn(Optional.empty());
        when(instrumentRegistry.upsert(MarketType.MACRO_DEPOSIT, "TP.DEPOSIT"))
                .thenReturn(Instrument.create(MarketType.MACRO_DEPOSIT, "TP.DEPOSIT"));
        when(macroRepository.save(any(MacroIndicator.class))).thenAnswer(inv -> inv.getArgument(0));

        depositService.synchronizeFromConfig();

        verify(instrumentRegistry).upsert(MarketType.MACRO_DEPOSIT, "TP.DEPOSIT");
    }
}
