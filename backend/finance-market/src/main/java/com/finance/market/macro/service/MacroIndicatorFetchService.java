package com.finance.market.macro.service;

import com.finance.market.core.dto.internal.EvdsDataResponse;
import com.finance.market.core.util.WindowedFetchPlanner;
import com.finance.market.macro.client.EvdsMacroClient;
import com.finance.market.macro.config.MacroProperties;
import com.finance.market.macro.dto.internal.MacroObservation;
import com.finance.market.macro.mapper.EvdsMacroMapper;
import com.finance.market.macro.model.MacroFrequency;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            return new FetchOutcome(0, 0, List.of());
        }
        LocalDate today = LocalDate.now();
        LocalDate start = oldestRequiredStart(indicators, today);
        Map<MacroFrequency, List<String>> codesByFrequency = indicators.stream()
                .collect(Collectors.groupingBy(
                        MacroIndicator::getFrequency,
                        Collectors.mapping(MacroIndicator::getCode, Collectors.toList())));
        List<EvdsDataResponse> responses = new ArrayList<>();
        for (List<String> sameFrequencyCodes : codesByFrequency.values()) {
            responses.addAll(fetchAcrossWindows(sameFrequencyCodes, start, today, properties.batchSize()));
        }
        int totalPoints = 0;
        List<String> changedCodes = new ArrayList<>();
        for (MacroIndicator indicator : indicators) {
            int inserted = persistObservations(indicator, responses);
            totalPoints += inserted;
            if (inserted > 0) {
                changedCodes.add(indicator.getCode());
            }
        }
        log.info("Macro refresh complete: {} indicators, {} new points, {} changed",
                indicators.size(), totalPoints, changedCodes.size());
        return new FetchOutcome(indicators.size(), totalPoints, List.copyOf(changedCodes));
    }

    @Transactional
    public int refreshOne(MacroIndicator indicator) {
        LocalDate today = LocalDate.now();
        LocalDate start = startFor(indicator, today);
        List<EvdsDataResponse> responses = fetchAcrossWindows(
                List.of(indicator.getCode()), start, today, 1);
        return persistObservations(indicator, responses);
    }

    private List<EvdsDataResponse> fetchAcrossWindows(List<String> codes,
                                                       LocalDate start,
                                                       LocalDate end,
                                                       int batchSize) {
        List<WindowedFetchPlanner.DateWindow> windows = WindowedFetchPlanner.planBackward(
                start, end, properties.maxDaysPerWindow());
        log.debug("Planned {} EVDS windows for {} codes from {} back to {}",
                windows.size(), codes.size(), end, start);
        List<EvdsDataResponse> responses = new ArrayList<>();
        for (WindowedFetchPlanner.DateWindow window : windows) {
            responses.addAll(client.fetchSeriesBatched(
                    codes, window.start(), window.end(), batchSize));
        }
        return responses;
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
        Set<LocalDate> seenDates = new HashSet<>();
        for (EvdsDataResponse response : responses) {
            for (MacroObservation observation : mapper.extract(response, indicator.getCode())) {
                if (seenDates.add(observation.observedAt())) {
                    observations.add(observation);
                }
            }
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

    public record FetchOutcome(int indicatorsTouched, int pointsInserted, List<String> changedCodes) { }
}
