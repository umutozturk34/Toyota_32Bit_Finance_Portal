package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketHistoryProvider;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopHistoryResolution;
import com.finance.market.viop.port.ViopMarketDataPort;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.shared.model.CandlePeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class ViopHistoryProvider implements MarketHistoryProvider {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final int MAX_HISTORY_YEARS = 5;

    private final ViopMarketDataPort marketData;
    private final ViopCandleRepository candleRepository;
    private final ViopContractRepository contractRepository;

    @Override
    public MarketType getMarketType() {
        return MarketType.VIOP;
    }

    @Override
    public List<ViopHistoryPoint> getHistory(String code, CandlePeriod period) {
        LocalDate earliestAllowed = LocalDate.now().minusYears(MAX_HISTORY_YEARS);
        LocalDate requestedStart = period.toStartDate();
        LocalDate effectiveStart = requestedStart.isBefore(earliestAllowed) ? earliestAllowed : requestedStart;
        return loadOrFetchRange(code, effectiveStart, LocalDate.now());
    }

    @Override
    public List<ViopHistoryPoint> getHistoryInRange(String code, LocalDate from, LocalDate to) {
        return loadOrFetchRange(code, from, to);
    }

    private List<ViopHistoryPoint> loadOrFetchRange(String code, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = to.isAfter(today) ? today : to;
        Optional<ViopCandle> latest = candleRepository.findFirstBySymbolOrderByCandleDateDesc(code);
        LocalDate latestStored = latest.map(c -> c.getCandleDate().toLocalDate()).orElse(null);

        if (latestStored == null || latestStored.isBefore(effectiveTo)) {
            LocalDate fetchFrom = latestStored != null ? latestStored.plusDays(1) : from;
            if (!fetchFrom.isAfter(effectiveTo)) {
                fetchAndPersist(code, fetchFrom, effectiveTo);
            }
        }

        LocalDateTime fromDT = from.atStartOfDay();
        LocalDateTime toDT = to.plusDays(1).atStartOfDay().minusSeconds(1);
        return candleRepository
                .findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(code, fromDT, toDT)
                .stream()
                .map(c -> new ViopHistoryPoint(c.getCandleDate(), c.getClose()))
                .toList();
    }

    @Transactional
    public int upsertTodayCandle(String code, java.math.BigDecimal close) {
        if (close == null) return 0;
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDateTime fromDT = today.atStartOfDay();
        java.time.LocalDateTime toDT = today.plusDays(1).atStartOfDay().minusSeconds(1);
        ViopCandle existing = candleRepository
                .findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(code, fromDT, toDT)
                .stream().findFirst().orElse(null);
        if (existing != null) {
            if (existing.getClose() != null && existing.getClose().compareTo(close) == 0) return 0;
            existing.setClose(close);
            candleRepository.save(existing);
            return 1;
        }
        ViopContract contract = contractRepository.findBySymbol(code).orElse(null);
        if (contract == null) return 0;
        candleRepository.save(ViopCandle.builder()
                .contract(contract)
                .candleDate(today.atStartOfDay())
                .close(close)
                .build());
        return 1;
    }

    @Transactional
    public int refreshCandlesUpTo(String code, LocalDate to) {
        Optional<ViopCandle> latest = candleRepository.findFirstBySymbolOrderByCandleDateDesc(code);
        LocalDate latestStored = latest.map(c -> c.getCandleDate().toLocalDate()).orElse(null);
        if (latestStored != null && latestStored.isAfter(to)) return 0;
        LocalDate from;
        if (latestStored == null) {
            from = to.minusYears(MAX_HISTORY_YEARS);
        } else if (latestStored.equals(to)) {
            from = to;
        } else {
            from = latestStored.plusDays(1);
        }
        return fetchAndPersist(code, from, to);
    }

    @Transactional
    public int fetchAndPersist(String code, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ISTANBUL).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ISTANBUL).minus(1, ChronoUnit.SECONDS).toInstant();
        List<ViopHistoryPoint> points;
        try {
            points = marketData.fetchHistory(code, ViopHistoryResolution.DAILY, fromInstant, toInstant);
        } catch (Exception e) {
            log.warn("VIOP history fetch failed symbol={} {}..{}: {}", code, from, to, e.getMessage());
            return 0;
        }
        if (points.isEmpty()) {
            return 0;
        }

        ViopContract contract = contractRepository.findBySymbol(code).orElse(null);
        if (contract == null) {
            log.warn("Cannot persist VIOP candles — unknown contract symbol {}", code);
            return 0;
        }

        java.util.Map<LocalDateTime, ViopCandle> existing = candleRepository
                .findBySymbolAndCandleDateIn(code, points.stream().map(ViopHistoryPoint::candleDate).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(ViopCandle::getCandleDate, c -> c, (a, b) -> a));

        List<ViopCandle> toSave = new ArrayList<>(points.size());
        for (ViopHistoryPoint point : points) {
            if (point.candleDate() == null || point.close() == null) continue;
            ViopCandle row = existing.get(point.candleDate());
            if (row != null) {
                row.setClose(point.close());
                toSave.add(row);
            } else {
                toSave.add(ViopCandle.builder()
                        .contract(contract)
                        .candleDate(point.candleDate())
                        .close(point.close())
                        .build());
            }
        }
        if (!toSave.isEmpty()) {
            candleRepository.saveAll(toSave);
            log.info("VIOP candles persisted symbol={} count={} range={}..{}",
                    code, toSave.size(), from, to);
        }
        return toSave.size();
    }
}
