package com.finance.market.macro.service;

import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.macro.client.EvdsMacroClient;
import com.finance.market.macro.config.MacroProperties;
import com.finance.market.macro.dto.internal.MacroObservation;
import com.finance.market.macro.mapper.EvdsMacroMapper;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.repository.MacroIndicatorPointRepository;
import com.finance.market.macro.repository.MacroIndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class MacroIndicatorFetchService {

    private final MacroIndicatorRepository indicatorRepository;
    private final MacroIndicatorPointRepository pointRepository;
    private final EvdsMacroClient client;
    private final EvdsMacroMapper mapper;
    private final MacroProperties properties;

    @Transactional
    public FetchOutcome refreshAll() {
        List<MacroIndicator> indicators = indicatorRepository.findAll();
        if (indicators.isEmpty()) {
            return new FetchOutcome(0, 0);
        }
        LocalDate today = LocalDate.now();
        LocalDate start = oldestRequiredStart(indicators, today);
        List<String> codes = indicators.stream().map(MacroIndicator::getCode).toList();
        List<EvdsDataResponse> responses = client.fetchSeriesBatched(
                codes, start, today, properties.batchSize());
        int totalPoints = 0;
        for (MacroIndicator indicator : indicators) {
            totalPoints += persistObservations(indicator, responses);
        }
        log.info("Macro refresh complete: {} indicators, {} new points", indicators.size(), totalPoints);
        return new FetchOutcome(indicators.size(), totalPoints);
    }

    @Transactional
    public int refreshOne(MacroIndicator indicator) {
        LocalDate today = LocalDate.now();
        LocalDate start = startFor(indicator, today);
        List<EvdsDataResponse> responses = client.fetchSeriesBatched(
                List.of(indicator.getCode()), start, today, 1);
        return persistObservations(indicator, responses);
    }

    private LocalDate oldestRequiredStart(List<MacroIndicator> indicators, LocalDate today) {
        return indicators.stream()
                .map(indicator -> startFor(indicator, today))
                .min(LocalDate::compareTo)
                .orElse(properties.backfillStartDate());
    }

    private LocalDate startFor(MacroIndicator indicator, LocalDate today) {
        LocalDate lastDate = indicator.getLastDate();
        if (lastDate == null) {
            return properties.backfillStartDate();
        }
        return lastDate.plusDays(1).isAfter(today) ? today : lastDate.plusDays(1);
    }

    private int persistObservations(MacroIndicator indicator, List<EvdsDataResponse> responses) {
        List<MacroObservation> observations = new ArrayList<>();
        for (EvdsDataResponse response : responses) {
            observations.addAll(mapper.extract(response, indicator.getCode()));
        }
        if (observations.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        for (MacroObservation observation : observations) {
            if (pointRepository.existsByIndicatorAndObservedAt(indicator, observation.observedAt())) {
                continue;
            }
            pointRepository.save(MacroIndicatorPoint.builder()
                    .indicator(indicator)
                    .observedAt(observation.observedAt())
                    .value(observation.value())
                    .build());
            indicator.recordObservation(observation.observedAt(), observation.value());
            inserted++;
        }
        return inserted;
    }

    public record FetchOutcome(int indicatorsTouched, int pointsInserted) { }
}
