package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.config.AppProperties;
import com.finance.backend.config.FundProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.exception.ExternalApiRequestException;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.util.ApiAssetValidator;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.TefasHelper;
import com.finance.backend.util.TrackedRefreshRunner;
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
public class FundSnapshotProcessor {

    private final TefasClient tefasClient;
    private final FundEntityWriter entityWriter;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId appZone;
    private final int eodCutoverHour;

    public FundSnapshotProcessor(TefasClient tefasClient,
                                 FundEntityWriter entityWriter,
                                 MarketCacheService<Fund, FundCandle> fundCacheService,
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
    }

    public void refreshAll() {
        long start = System.currentTimeMillis();
        LocalDate today = today();
        log.info("Starting bulk fund snapshot update for {}", today);

        int byfSaved = bulkUpdateAndAutoTrackBYF(today);
        Set<String> trackedCodes = Set.copyOf(
                trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND));
        int yatSaved = bulkUpdateForTrackedYAT(today, trackedCodes);

        log.info("[TIMING] Fund snapshot update took {}s (BYF saved={}, YAT saved={})",
                (System.currentTimeMillis() - start) / 1000,
                byfSaved < 0 ? "FAILED" : byfSaved, yatSaved < 0 ? "FAILED" : yatSaved);
    }

    public void refreshOne(String fundCode) {
        TrackedRefreshRunner.refreshSnapshot(fundCode, CodeNormalizer::upper, normalized -> {
            LocalDate today = today();
            for (FundType type : List.of(FundType.YAT, FundType.BYF)) {
                List<TefasFundDto> funds = tefasClient.post(type, normalized, today, today);
                if (funds.isEmpty()) continue;
                Fund saved = transactionTemplate.execute(s -> entityWriter.saveSnapshot(funds.getFirst(), type));
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
        Fund persisted = transactionTemplate.execute(s -> entityWriter.saveSnapshot(dto, FundType.BYF));
        if (persisted == null) return false;
        entityWriter.ensureByfTracked(persisted.getFundCode(), persisted.getName());
        fundCacheService.putSnapshot(persisted.getFundCode(), persisted);
        return true;
    }

    private boolean persistYat(TefasFundDto dto) {
        Fund persisted = transactionTemplate.execute(s -> entityWriter.saveSnapshot(dto, FundType.YAT));
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
        } catch (ExternalApiRequestException e) {
            log.error("WAF block on {} snapshot, propagating: {}", fundType, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to bulk fetch {} snapshot", fundType, e);
            return -1;
        }
    }
}
