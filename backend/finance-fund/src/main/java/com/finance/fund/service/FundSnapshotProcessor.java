package com.finance.fund.service;
import com.finance.common.service.TrackedAssetQueryService;

import com.finance.cache.service.MarketCacheService;

import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.fund.client.TefasClient;
import com.finance.common.config.AppProperties;
import com.finance.fund.config.FundProperties;
import com.finance.fund.dto.external.TefasFundDto;
import com.finance.fund.model.Fund;
import com.finance.fund.model.FundType;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.util.ApiAssetValidator;
import com.finance.common.util.CodeNormalizer;
import com.finance.fund.util.TefasHelper;
import com.finance.common.util.TrackedRefreshRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Log4j2
@Component
public class FundSnapshotProcessor implements MarketSnapshotProcessor {

    private final TefasClient tefasClient;
    private final FundEntityWriter entityWriter;
    private final MarketCacheService<Fund> fundCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId appZone;
    private final int eodCutoverHour;
    private final int holidayLookbackDays;

    public FundSnapshotProcessor(TefasClient tefasClient,
                                 FundEntityWriter entityWriter,
                                 MarketCacheService<Fund> fundCacheService,
                                 TrackedAssetQueryService trackedAssetQueryService,
                                 TransactionTemplate transactionTemplate,
                                 AppProperties appProperties,
                                 FundProperties fundProperties) {
        this.tefasClient = tefasClient;
        this.entityWriter = entityWriter;
        this.fundCacheService = fundCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.transactionTemplate = transactionTemplate;
        this.appZone = ZoneId.of(appProperties.getTimezone());
        this.eodCutoverHour = fundProperties.getTefasEodCutoverHour();
        this.holidayLookbackDays = fundProperties.getHolidayLookbackDays();
    }

    public void refreshAll() {
        long start = System.currentTimeMillis();
        LocalDate cursor = today();
        log.info("Starting bulk fund snapshot update for {}", cursor);

        Set<String> trackedCodes = Set.copyOf(
                trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND));
        int byfSaved = -1;
        int yatSaved = -1;

        for (int attempt = 0; attempt <= holidayLookbackDays; attempt++) {
            byfSaved = bulkUpdateAndAutoTrackBYF(cursor);
            yatSaved = bulkUpdateForTrackedYAT(cursor, trackedCodes);
            if (byfSaved > 0 || yatSaved > 0) break;
            if (attempt == holidayLookbackDays) break;
            log.info("No fund data for {}, walking back one day (TEFAS likely closed for holiday)", cursor);
            cursor = cursor.minusDays(1);
        }

        log.info("[TIMING] Fund snapshot update took {}s (effective date {}, BYF saved={}, YAT saved={})",
                (System.currentTimeMillis() - start) / 1000, cursor,
                byfSaved < 0 ? "FAILED" : byfSaved, yatSaved < 0 ? "FAILED" : yatSaved);
    }

    public void refreshOne(String fundCode) {
        TrackedRefreshRunner.refreshSnapshot(fundCode, CodeNormalizer::upper, normalized -> {
            LocalDate today = today();
            for (FundType type : List.of(FundType.YAT, FundType.BYF)) {
                List<TefasFundDto> funds = tefasClient.post(type, normalized, today, today);
                if (funds.isEmpty()) continue;
                TefasFundDto dto = funds.getFirst();
                Fund saved = transactionTemplate.execute(s -> {
                    Fund f = entityWriter.saveSnapshot(dto, type);
                    entityWriter.upsertCandleFromDto(f, type, dto);
                    return f;
                });
                fundCacheService.putSnapshot(saved.getFundCode(), saved);
                return true;
            }
            log.warn("No snapshot data found for tracked fund {}", normalized);
            return false;
        }, log, "fund");
    }

    public boolean exists(String fundCode) {
        return ApiAssetValidator.validate(fundCode, true, code -> {
            LocalDate today = today();
            return !tefasClient.post(FundType.YAT, code, today, today).isEmpty()
                    || !tefasClient.post(FundType.BYF, code, today, today).isEmpty();
        }, log, "Fund");
    }

    private LocalDate today() {
        return TefasHelper.findLastBusinessDay(LocalDate.now(appZone), appZone, eodCutoverHour);
    }

    private int bulkUpdateAndAutoTrackBYF(LocalDate today) {
        return executeBulk(FundType.BYF, today, dto -> true, this::persistByf);
    }

    private int bulkUpdateForTrackedYAT(LocalDate today, Set<String> trackedCodes) {
        return executeBulk(FundType.YAT, today,
                dto -> trackedCodes.contains(dto.fundCode()),
                this::persistYat);
    }

    private boolean persistByf(TefasFundDto dto) {
        Fund persisted = transactionTemplate.execute(s -> {
            Fund f = entityWriter.saveSnapshot(dto, FundType.BYF);
            entityWriter.upsertCandleFromDto(f, FundType.BYF, dto);
            return f;
        });
        if (persisted == null) return false;
        entityWriter.ensureByfTracked(persisted.getFundCode(), persisted.getName());
        fundCacheService.putSnapshot(persisted.getFundCode(), persisted);
        return true;
    }

    private boolean persistYat(TefasFundDto dto) {
        Fund persisted = transactionTemplate.execute(s -> {
            Fund f = entityWriter.saveSnapshot(dto, FundType.YAT);
            entityWriter.upsertCandleFromDto(f, FundType.YAT, dto);
            return f;
        });
        if (persisted == null) return false;
        fundCacheService.putSnapshot(persisted.getFundCode(), persisted);
        return true;
    }

    private int executeBulk(FundType fundType, LocalDate today,
                            Predicate<TefasFundDto> include,
                            Predicate<TefasFundDto> persist) {
        try {
            List<TefasFundDto> bulk = tefasClient.bulkFetch(fundType, today, today);
            int saved = 0;
            for (TefasFundDto dto : bulk) {
                if (!include.test(dto)) continue;
                try {
                    if (persist.test(dto)) saved++;
                } catch (Exception e) {
                    log.error("Failed to persist {} snapshot for fund {}",
                            fundType, dto.fundCode(), e);
                }
            }
            log.info("Bulk {} snapshot: {} rows fetched, {} saved", fundType, bulk.size(), saved);
            return saved;
        } catch (CallNotPermittedException e) {
            log.warn("TEFAS circuit breaker is OPEN, aborting {} snapshot", fundType);
            return -1;
        }
    }
}
