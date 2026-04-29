package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.config.FundProperties;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TrackedAssetUpsertCommand;
import com.finance.backend.exception.ExternalApiRequestException;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.util.ApiAssetValidator;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.TefasHelper;
import com.finance.backend.util.TrackedRefreshRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Log4j2
@Service
public class FundSnapshotService implements SnapshotBatchRefresher, AssetExistenceChecker {

    private final TefasClient tefasClient;
    private final FundMapper fundMapper;
    private final FundRepository fundRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final FundChangeCalculator fundChangeCalculator;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId appZone;
    private final int eodCutoverHour;

    public FundSnapshotService(TefasClient tefasClient,
                               FundMapper fundMapper,
                               FundRepository fundRepository,
                               MarketCacheService<Fund, FundCandle> fundCacheService,
                               TrackedAssetQueryService trackedAssetQueryService,
                               TrackedAssetCommandService trackedAssetCommandService,
                               FundChangeCalculator fundChangeCalculator,
                               TransactionTemplate transactionTemplate,
                               FundProperties fundProperties,
                               @Value("${app.timezone}") String timezone) {
        this.tefasClient = tefasClient;
        this.fundMapper = fundMapper;
        this.fundRepository = fundRepository;
        this.fundCacheService = fundCacheService;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.trackedAssetCommandService = trackedAssetCommandService;
        this.fundChangeCalculator = fundChangeCalculator;
        this.transactionTemplate = transactionTemplate;
        this.appZone = ZoneId.of(timezone);
        this.eodCutoverHour = fundProperties.getTefasEodCutoverHour();
    }

    @Override
    public boolean existsInApi(String fundCode) {
        return ApiAssetValidator.validate(fundCode, true, code -> {
            LocalDate today = TefasHelper.findLastBusinessDay(LocalDate.now(), appZone, eodCutoverHour);
            if (!tefasClient.post(FundType.YAT, code, today, today).isEmpty()) {
                return true;
            }
            return !tefasClient.post(FundType.BYF, code, today, today).isEmpty();
        }, log, "Fund");
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FUND;
    }

    @Override
    public void refreshAll() {
        long start = System.currentTimeMillis();
        LocalDate today = TefasHelper.findLastBusinessDay(LocalDate.now(), appZone, eodCutoverHour);
        log.info("Starting bulk fund snapshot update for {}", today);

        int byfSaved = bulkUpdateAndAutoTrackBYF(today);
        Set<String> trackedCodes = Set.copyOf(
                trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND));
        int yatSaved = bulkUpdateForTrackedYAT(today, trackedCodes);

        log.info("[TIMING] Fund snapshot update took {}s (BYF saved={}, YAT saved={})",
                (System.currentTimeMillis() - start) / 1000,
                byfSaved < 0 ? "FAILED" : byfSaved, yatSaved < 0 ? "FAILED" : yatSaved);
    }

    private int bulkUpdateAndAutoTrackBYF(LocalDate today) {
        return executeBulkSnapshot(FundType.BYF, today, dto -> true, dto -> {
            Fund persisted = persistSnapshot(dto, FundType.BYF);
            if (persisted == null) return false;
            ensureByfTracked(persisted.getFundCode(), persisted.getName());
            fundCacheService.putSnapshot(persisted.getFundCode(), persisted);
            return true;
        });
    }

    private int bulkUpdateForTrackedYAT(LocalDate today, Set<String> trackedCodes) {
        return executeBulkSnapshot(FundType.YAT, today,
                dto -> trackedCodes.contains(dto.fundCode()),
                dto -> {
                    Fund persisted = persistSnapshot(dto, FundType.YAT);
                    if (persisted == null) return false;
                    fundCacheService.putSnapshot(persisted.getFundCode(), persisted);
                    return true;
                });
    }

    private int executeBulkSnapshot(FundType fundType, LocalDate today,
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

    private Fund persistSnapshot(TefasFundDto dto, FundType fundType) {
        return transactionTemplate.execute(status -> saveFundSnapshot(dto, fundType));
    }

    @Override
    public void refreshSnapshot(String fundCode) {
        TrackedRefreshRunner.refreshSnapshot(fundCode, CodeNormalizer::upper, normalized -> {
            LocalDate today = TefasHelper.findLastBusinessDay(LocalDate.now(), appZone, eodCutoverHour);
            List<TefasFundDto> yatFunds = tefasClient.post(FundType.YAT, normalized, today, today);
            FundType fundType = FundType.YAT;
            TefasFundDto dto = yatFunds.isEmpty() ? null : yatFunds.getFirst();
            if (dto == null) {
                List<TefasFundDto> byfFunds = tefasClient.post(FundType.BYF, normalized, today, today);
                fundType = FundType.BYF;
                dto = byfFunds.isEmpty() ? null : byfFunds.getFirst();
            }
            if (dto == null) {
                log.warn("No snapshot data found for tracked fund {}", normalized);
                return false;
            }
            FundType finalFundType = fundType;
            TefasFundDto selectedDto = dto;
            Fund saved = transactionTemplate.execute(status -> saveFundSnapshot(selectedDto, finalFundType));
            fundCacheService.putSnapshot(saved.getFundCode(), saved);
            return true;
        }, log, "fund");
    }

    private Fund saveFundSnapshot(TefasFundDto dto, FundType fundType) {
        LocalDateTime now = LocalDateTime.now();
        Fund fund = fundRepository.findById(dto.fundCode()).orElse(null);
        Fund toPersist;
        if (fund != null) {
            fundMapper.updateEntity(fund, dto, fundType, now);
            toPersist = fund;
        } else {
            toPersist = fundMapper.toEntity(dto, fundType, now);
        }
        toPersist.setChangePercent(fundChangeCalculator.calculateChangePercent(dto.fundCode(), dto.price()));
        fundRepository.save(toPersist);
        log.debug("Saved snapshot: {} ({}) - {}", dto.fundCode(), fundType, dto.price());
        return toPersist;
    }

    private void ensureByfTracked(String fundCode, String tefasName) {
        if (trackedAssetQueryService.getTrackedAsset(TrackedAssetType.FUND, fundCode).isPresent()) {
            return;
        }

        trackedAssetCommandService.upsert(TrackedAssetUpsertCommand.builder()
            .assetType(TrackedAssetType.FUND)
            .assetCode(fundCode)
            .displayName(tefasName)
            .enabled(true)
            .sortOrder(9999)
            .build());

        log.info("Auto-added BYF fund to tracked assets: {}", fundCode);
    }
}
