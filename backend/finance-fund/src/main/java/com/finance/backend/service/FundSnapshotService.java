package com.finance.backend.service;

import com.finance.backend.client.TefasClient;
import com.finance.backend.dto.external.TefasFundDto;
import com.finance.backend.dto.internal.TrackedAssetUpsertCommand;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.mapper.FundMapper;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import com.finance.backend.model.FundType;
import com.finance.backend.model.MarketType;
import com.finance.backend.model.TrackedAssetType;
import com.finance.backend.repository.FundRepository;
import com.finance.backend.util.ApiAssetValidator;
import com.finance.backend.util.BatchLogHelper;
import com.finance.backend.util.BatchUpdateRunner;
import com.finance.backend.util.CodeNormalizer;
import com.finance.backend.util.MarketBatchRunner;
import com.finance.backend.util.TefasHelper;
import com.finance.backend.util.TrackedRefreshRunner;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Service
public class FundSnapshotService implements SnapshotBatchRefresher {

    private final TefasClient tefasClient;
    private final FundMapper fundMapper;
    private final FundRepository fundRepository;
    private final MarketCacheService<Fund, FundCandle> fundCacheService;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final TrackedAssetCommandService trackedAssetCommandService;
    private final FundChangeCalculator fundChangeCalculator;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId appZone;

    public FundSnapshotService(TefasClient tefasClient,
                               FundMapper fundMapper,
                               FundRepository fundRepository,
                               MarketCacheService<Fund, FundCandle> fundCacheService,
                               TrackedAssetQueryService trackedAssetQueryService,
                               TrackedAssetCommandService trackedAssetCommandService,
                               FundChangeCalculator fundChangeCalculator,
                               TransactionTemplate transactionTemplate,
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
    }

    public boolean existsInApi(String fundCode) {
        return ApiAssetValidator.validate(fundCode, true, code -> {
            LocalDate today = findLastBusinessDay(LocalDate.now());
            List<TefasFundDto> yat = fetchTefas(FundType.YAT, code, today, today);
            if (!yat.isEmpty()) return true;
            List<TefasFundDto> byf = fetchTefas(FundType.BYF, code, today, today);
            return !byf.isEmpty();
        }, log, "Fund");
    }

    @Override
    public MarketType getMarketType() {
        return MarketType.FUND;
    }

    @Override
    public void refreshAll() {
        long snapshotStart = System.currentTimeMillis();
        LocalDate today = findLastBusinessDay(LocalDate.now());
        log.info("Starting fund snapshot update for {}", today);
        BatchUpdateRunner.Result byfResult = new BatchUpdateRunner.Result(0, 0, List.of());
        Set<String> byfCodes = Set.of();

        try {
            List<TefasFundDto> byfFunds = fetchTefas(FundType.BYF, null, today, today);
            byfCodes = byfFunds.stream().map(TefasFundDto::fundCode).collect(Collectors.toSet());
            byfResult = MarketBatchRunner.run(
                    byfFunds,
                    dto -> {
                        Fund saved = transactionTemplate.execute(status -> saveFundSnapshot(dto, FundType.BYF));
                        ensureByfTracked(saved.getFundCode(), saved.getName());
                        fundCacheService.putSnapshot(saved.getFundCode(), saved);
                    },
                    TefasFundDto::fundCode,
                    log, "BYF", "snapshot", 5);
        } catch (CallNotPermittedException e) {
            log.warn("TEFAS circuit breaker is OPEN, aborting snapshot update");
            return;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch BYF funds", e);
        }

        Set<String> handledByfCodes = byfCodes;
        List<String> yatCodes = trackedAssetQueryService.getEnabledCodes(TrackedAssetType.FUND).stream()
                .filter(code -> !handledByfCodes.contains(code))
                .toList();

        if (yatCodes.isEmpty()) {
            log.info("No YAT-only fund codes to update (all handled as BYF)");
        } else {
            BatchUpdateRunner.Result yatResult = MarketBatchRunner.run(
                    yatCodes,
                    code -> {
                        List<TefasFundDto> yatFunds = fetchTefas(FundType.YAT, code, today, today);
                        if (!yatFunds.isEmpty()) {
                            Fund saved = transactionTemplate.execute(status -> saveFundSnapshot(yatFunds.getFirst(), FundType.YAT));
                            fundCacheService.putSnapshot(saved.getFundCode(), saved);
                        }
                    },
                    Function.identity(),
                    log, "YAT", "snapshot", 5);

            byfResult = new BatchUpdateRunner.Result(
                    byfResult.successCount() + yatResult.successCount(),
                    byfResult.failCount() + yatResult.failCount(),
                    Stream.concat(byfResult.failedItems().stream(), yatResult.failedItems().stream()).toList());
        }

        log.info("[TIMING] Fund snapshot update took {}s (BYF={}, YAT={})",
                (System.currentTimeMillis() - snapshotStart) / 1000,
                handledByfCodes.size(), yatCodes.size());
        BatchLogHelper.logSummary(log, "Fund snapshot update", byfResult);
    }

    public void refreshTrackedFundSnapshot(String fundCode) {
        TrackedRefreshRunner.refreshSnapshot(fundCode, CodeNormalizer::upper, normalized -> {
            LocalDate today = findLastBusinessDay(LocalDate.now());
            List<TefasFundDto> yatFunds = fetchTefas(FundType.YAT, normalized, today, today);
            FundType fundType = FundType.YAT;
            TefasFundDto dto = yatFunds.isEmpty() ? null : yatFunds.getFirst();
            if (dto == null) {
                List<TefasFundDto> byfFunds = fetchTefas(FundType.BYF, normalized, today, today);
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

    private List<TefasFundDto> fetchTefas(FundType fundType, String fundCode,
                                          LocalDate startDate, LocalDate endDate) {
        return TefasHelper.fetchTefas(tefasClient, fundType, fundCode, startDate, endDate);
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

    private LocalDate findLastBusinessDay(LocalDate from) {
        return TefasHelper.findLastBusinessDay(from, appZone);
    }
}
