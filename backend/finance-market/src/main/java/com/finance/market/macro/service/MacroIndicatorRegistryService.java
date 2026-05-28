package com.finance.market.macro.service;

import com.finance.common.model.Instrument;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.macro.config.MacroProperties;
import com.finance.market.macro.config.MacroProperties.IndicatorDefinition;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.repository.MacroIndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reconciles the macro indicator catalogue with the configured definitions: upserting each by code
 * (creating the cross-module {@link Instrument} for new ones) so config is the source of truth.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class MacroIndicatorRegistryService {

    private final MacroIndicatorRepository macroRepository;
    private final AssetRegistryService instrumentRegistry;
    private final MacroProperties properties;

    @Transactional
    public List<MacroIndicator> synchronizeFromConfig() {
        List<IndicatorDefinition> definitions = properties.indicators();
        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }
        return definitions.stream().map(this::upsert).toList();
    }

    @Transactional
    public MacroIndicator upsert(IndicatorDefinition definition) {
        return macroRepository.findByCode(definition.code())
                .map(existing -> updateExisting(existing, definition))
                .orElseGet(() -> createNew(definition));
    }

    private MacroIndicator createNew(IndicatorDefinition definition) {
        Instrument instrument = instrumentRegistry.upsert(
                definition.category().instrumentType(), definition.code());
        MacroIndicator indicator = MacroIndicator.builder()
                .instrument(instrument)
                .code(definition.code())
                .label(definition.label())
                .category(definition.category())
                .unit(definition.unit())
                .frequency(definition.frequency())
                .currency(definition.currency())
                .maturity(definition.maturity())
                .prominent(definition.prominent())
                .build();
        log.info("Registering macro indicator code={} label={}", definition.code(), definition.label());
        return macroRepository.save(indicator);
    }

    private MacroIndicator updateExisting(MacroIndicator existing, IndicatorDefinition definition) {
        existing.applyDefinition(
                definition.label(),
                definition.category(),
                definition.unit(),
                definition.frequency(),
                definition.currency(),
                definition.maturity(),
                definition.prominent());
        return existing;
    }
}
