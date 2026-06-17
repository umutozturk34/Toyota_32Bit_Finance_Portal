package com.finance.market.fund.service;

import com.finance.common.config.AppProperties;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.TrackedAssetQueryService;
import com.finance.market.fund.client.TefasClient;
import com.finance.market.fund.config.FundProperties;
import com.finance.market.fund.dto.external.TefasFundAllocationDto;
import com.finance.market.fund.dto.external.TefasFundInfoDto;
import com.finance.market.fund.dto.external.TefasFundProfileDto;
import com.finance.market.fund.dto.external.TefasFundReturnsDto;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundAllocation;
import com.finance.market.fund.model.FundType;
import com.finance.market.fund.repository.FundAllocationRepository;
import com.finance.market.fund.repository.FundRepository;
import com.finance.market.fund.util.TefasHelper;
import com.finance.market.core.util.MarketBatchRunner;
import com.finance.shared.service.TaskTrackingService;
import com.finance.shared.util.BatchUpdateRunner;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enriches stored funds with TEFAS detail data: trailing returns/risk, asset allocations, and
 * profile/info. Allocation fetches walk back day-by-day to skip non-publishing days, bounded by a
 * configured walkback. Enrichment failures are logged and skipped, never aborting the whole pass.
 */
@Log4j2
@Service
public class FundDetailEnrichmentService {

    private final TefasClient tefasClient;
    private final FundRepository fundRepository;
    private final FundAllocationRepository allocationRepository;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final MarketCacheService<Fund> fundCacheService;
    private final FundProperties fundProperties;
    private final ZoneId appZone;
    private final int eodCutoverHour;
    private final TaskTrackingService taskTracker;
    /** Guards against two profile back-fills running at once (e.g. a daily refresh firing while a cold-start fill
     *  is still draining), which would double-fetch and waste the scarce TEFAS rate budget. */
    private final AtomicBoolean profileBackfillRunning = new AtomicBoolean(false);

    /** Min sample before the batch failure-rate guard engages on the profile back-fill. */
    private static final int PROFILE_BATCH_MIN_SAMPLE = 25;

    public FundDetailEnrichmentService(TefasClient tefasClient,
                                       FundRepository fundRepository,
                                       FundAllocationRepository allocationRepository,
                                       TrackedAssetQueryService trackedAssetQueryService,
                                       MarketCacheService<Fund> fundCacheService,
                                       FundProperties fundProperties,
                                       AppProperties appProperties,
                                       TaskTrackingService taskTracker) {
        this.tefasClient = tefasClient;
        this.fundRepository = fundRepository;
        this.allocationRepository = allocationRepository;
        this.trackedAssetQueryService = trackedAssetQueryService;
        this.fundCacheService = fundCacheService;
        this.fundProperties = fundProperties;
        this.appZone = ZoneId.of(appProperties.getTimezone());
        this.eodCutoverHour = fundProperties.getTefasEodCutoverHour();
        this.taskTracker = taskTracker;
    }

    private LocalDate effectiveDate(LocalDate requested) {
        return TefasHelper.findLastBusinessDay(requested, appZone, eodCutoverHour);
    }

    @Transactional
    public int enrichReturnsAndRisk() {
        Set<String> existingCodes = loadExistingFundCodes();
        if (existingCodes.isEmpty()) {
            log.info("No funds in DB, skipping returns enrichment");
            return 0;
        }
        int updated = 0;
        for (FundType type : FundType.values()) {
            try {
                List<TefasFundReturnsDto> rows = tefasClient.fetchReturns(type);
                updated += applyReturns(rows, existingCodes);
            } catch (Exception e) {
                log.warn("Fund returns enrichment failed for {}: {}", type, e.getMessage());
            }
        }
        log.info("Fund returns/risk enrichment: {} rows updated (tracked={}, source=TEFAS)", updated, existingCodes.size());
        return updated;
    }

    /**
     * Bulk back-fills the per-fund TEFAS profile (settlement valör, ISIN, KAP link, trade window, risk) for
     * every tracked fund still missing it. Unlike returns/risk — which arrive in one bulk TEFAS call — the
     * profile endpoint is per-fund, so the fetches run on a bounded pool. {@code isinCode} is profile-only,
     * so an enriched fund is skipped on later cycles: the first refresh is the only heavy one, and a newly
     * added fund is filled on the next refresh. (Valör/seans are effectively static, so re-fetching enriched
     * funds every cycle would be wasteful; the lazy detail-open path covers the instant case.)
     *
     * @return number of funds whose profile was filled this pass
     */
    public int enrichMissingProfiles() {
        Set<String> tracked = loadExistingFundCodes();
        if (tracked.isEmpty()) return 0;
        List<String> targets = fundRepository.findFundCodesMissingProfile().stream()
                .filter(tracked::contains)
                .toList();
        if (targets.isEmpty()) {
            log.info("Fund profile back-fill: all tracked funds already enriched");
            return 0;
        }
        log.info("Fund profile back-fill: {} funds missing profile (parallelism={})",
                targets.size(), fundProperties.getProfileEnrichParallelism());
        BatchUpdateRunner.Result result = MarketBatchRunner.runParallel(
                targets, this::enrichProfileOnly, code -> code,
                log, "Fund", "profile-enrich", PROFILE_BATCH_MIN_SAMPLE,
                fundProperties.getProfileEnrichParallelism());
        log.info("Fund profile back-fill done: {} enriched, {} failed", result.successCount(), result.failCount());
        return result.successCount();
    }

    /**
     * Fire-and-forget wrapper around {@link #enrichMissingProfiles()} so the cold-start / full refresh never
     * blocks on it. valör/ISIN/seans/KAP come ONLY from TEFAS's per-fund profile endpoint — there is no bulk
     * variant (a profile call without {@code fonKodu} returns empty; the bulk snapshot/returns/allocation calls
     * carry price/size/returns/RISK but not these) — and the shared TEFAS rate limiter caps calls at 1 / 2s, so
     * back-filling the whole universe is inherently a (funds × 2s) job (~1h for ~2000 funds). Running it on the
     * task executor keeps {@code refreshAll} — and thus the init/daily fund update — fast (just the bulk steps);
     * profiles trickle in afterwards and the lazy detail-open path ({@link #enrichSingleFundDetailsAsync}) fills
     * any fund a user actually views instantly. It runs under task-tracking so it shows in the admin task panel,
     * and the guard flag prevents two overlapping back-fills from double-fetching.
     */
    @Async("taskExecutor")
    public void enrichMissingProfilesAsync() {
        if (!profileBackfillRunning.compareAndSet(false, true)) {
            log.info("Fund profile back-fill already running, skipping this trigger");
            return;
        }
        try {
            taskTracker.runTracked("fund-profile-backfill",
                    "Fund valör/ISIN/seans back-fill (per-fund TEFAS, rate-limited)",
                    this::enrichMissingProfiles);
        } finally {
            profileBackfillRunning.set(false);
        }
    }

    /** Fetches and applies only the TEFAS profile for one fund (one HTTP call); persists + refreshes cache. */
    private void enrichProfileOnly(String fundCode) {
        Fund fund = fundRepository.findById(fundCode).orElse(null);
        if (fund == null) return;
        FundType type = fund.getFundType() != null ? fund.getFundType() : FundType.YAT;
        TefasFundProfileDto profile = tefasClient.fetchProfile(type, fundCode);
        if (profile == null) return;
        applyProfile(fund, profile);
        Fund saved = fundRepository.save(fund);
        fundCacheService.putSnapshot(saved.getFundCode(), saved);
    }

    @Transactional
    public int enrichReturnsAndRiskForFund(String fundCode) {
        Fund fund = fundRepository.findById(fundCode).orElse(null);
        if (fund == null) return 0;
        FundType type = fund.getFundType() != null ? fund.getFundType() : FundType.YAT;
        try {
            List<TefasFundReturnsDto> rows = tefasClient.fetchReturns(type);
            return applyReturns(rows, Set.of(fundCode));
        } catch (Exception e) {
            log.warn("Single-fund returns enrichment failed for {}: {}", fundCode, e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int enrichAllocations(LocalDate date) {
        Set<String> existingCodes = loadExistingFundCodes();
        if (existingCodes.isEmpty()) {
            log.info("No funds in DB, skipping allocation enrichment");
            return 0;
        }
        int updated = 0;
        int walkbackDays = fundProperties.getAllocationWalkbackDays();
        LocalDate startDate = effectiveDate(date);
        for (FundType type : FundType.values()) {
            try {
                LocalDate cursor = startDate;
                List<TefasFundAllocationDto> rows = List.of();
                for (int attempt = 0; attempt <= walkbackDays; attempt++) {
                    rows = tefasClient.fetchAllocations(type, cursor);
                    if (!rows.isEmpty()) break;
                    cursor = cursor.minusDays(1);
                }
                if (rows.isEmpty()) {
                    log.warn("Fund allocation: no data for {} within {} days walkback ending {}", type, walkbackDays, date);
                    continue;
                }
                updated += applyAllocations(rows, existingCodes, cursor);
            } catch (Exception e) {
                log.warn("Fund allocation enrichment failed for {}: {}", type, e.getMessage());
            }
        }
        log.info("Fund allocation enrichment: {} funds persisted", updated);
        return updated;
    }

    @Transactional
    public int enrichAllocationsForFund(String fundCode, LocalDate date) {
        Fund fund = fundRepository.findById(fundCode).orElse(null);
        if (fund == null) return 0;
        FundType type = fund.getFundType() != null ? fund.getFundType() : FundType.YAT;
        Set<String> single = Set.of(fundCode);
        int walkbackDays = fundProperties.getAllocationWalkbackDays();
        try {
            LocalDate cursor = effectiveDate(date);
            List<TefasFundAllocationDto> rows = List.of();
            for (int attempt = 0; attempt <= walkbackDays; attempt++) {
                rows = tefasClient.fetchAllocations(type, cursor);
                if (rows.stream().anyMatch(dto -> fundCode.equals(dto.fundCode()))) break;
                cursor = cursor.minusDays(1);
            }
            if (rows.isEmpty()) {
                log.warn("Single-fund allocation: no data for {} within {} days walkback ending {}", fundCode, walkbackDays, date);
                return 0;
            }
            return applyAllocations(rows, single, cursor);
        } catch (Exception e) {
            log.warn("Single-fund allocation enrichment failed for {}: {}", fundCode, e.getMessage());
            return 0;
        }
    }

    /**
     * Fire-and-forget variant of {@link #enrichSingleFundDetails(String)} for the detail-open path: it runs on
     * the shared task executor so the read request returns immediately instead of blocking on two sequential
     * TEFAS calls. The fetched profile is persisted + cached, so it surfaces on the next view/refetch. Failures
     * are logged and swallowed — a background enrichment must never fail the request that triggered it.
     */
    @Async("taskExecutor")
    public void enrichSingleFundDetailsAsync(String fundCode) {
        try {
            enrichSingleFundDetails(fundCode);
        } catch (Exception e) {
            log.debug("Async fund detail enrichment failed for {}: {}", fundCode, e.getMessage());
        }
    }

    @Transactional
    public Fund enrichSingleFundDetails(String fundCode) {
        Fund fund = fundRepository.findById(fundCode).orElse(null);
        if (fund == null) return null;
        FundType type = fund.getFundType() != null ? fund.getFundType() : FundType.YAT;
        try {
            TefasFundInfoDto info = tefasClient.fetchInfo(type, fundCode);
            if (info != null) applyInfo(fund, info);
        } catch (Exception e) {
            log.debug("fetchInfo failed for {}: {}", fundCode, e.getMessage());
        }
        try {
            TefasFundProfileDto profile = tefasClient.fetchProfile(type, fundCode);
            if (profile != null) applyProfile(fund, profile);
        } catch (Exception e) {
            log.debug("fetchProfile failed for {}: {}", fundCode, e.getMessage());
        }
        Fund saved = fundRepository.save(fund);
        fundCacheService.putSnapshot(saved.getFundCode(), saved);
        return saved;
    }

    /** Tracked fund codes that also exist in the DB (intersection), so enrichment targets only real rows. */
    private Set<String> loadExistingFundCodes() {
        Set<String> tracked = new HashSet<>(trackedAssetQueryService.getCodes(TrackedAssetType.FUND));
        Set<String> persisted = new HashSet<>(fundRepository.findAllFundCodes());
        tracked.retainAll(persisted);
        return tracked;
    }

    private int applyReturns(List<TefasFundReturnsDto> rows, Set<String> existingCodes) {
        List<TefasFundReturnsDto> matched = rows.stream()
                .filter(dto -> dto.fundCode() != null && existingCodes.contains(dto.fundCode()))
                .toList();
        if (matched.isEmpty()) return 0;
        Map<String, Fund> existing = fundRepository.findAllById(
                        matched.stream().map(TefasFundReturnsDto::fundCode).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Fund::getFundCode, f -> f));
        List<Fund> toSave = new ArrayList<>(matched.size());
        for (TefasFundReturnsDto dto : matched) {
            Fund fund = existing.get(dto.fundCode());
            if (fund == null) continue;
            fund.setReturn1m(dto.return1m());
            fund.setReturn3m(dto.return3m());
            fund.setReturn6m(dto.return6m());
            fund.setReturn1y(dto.return1y());
            fund.setReturnYtd(dto.returnYtd());
            fund.setReturn3y(dto.return3y());
            fund.setReturn5y(dto.return5y());
            fund.setRiskValue(parseRisk(dto.riskValue()));
            fund.setSubCategory(dto.subCategory());
            if (fund.getName() == null && dto.name() != null) fund.setName(dto.name());
            toSave.add(fund);
        }
        fundRepository.saveAll(toSave);
        for (Fund f : toSave) fundCacheService.putSnapshot(f.getFundCode(), f);
        return toSave.size();
    }

    private int applyAllocations(List<TefasFundAllocationDto> rows, Set<String> existingCodes, LocalDate date) {
        List<TefasFundAllocationDto> matched = rows.stream()
                .filter(dto -> dto.fundCode() != null
                        && !dto.allocations().isEmpty()
                        && existingCodes.contains(dto.fundCode()))
                .toList();
        if (matched.isEmpty()) return 0;
        Set<String> codes = matched.stream().map(TefasFundAllocationDto::fundCode).collect(java.util.stream.Collectors.toSet());
        allocationRepository.deleteByFundCodeIn(codes);
        List<FundAllocation> toSave = new ArrayList<>();
        for (TefasFundAllocationDto dto : matched) {
            for (Map.Entry<String, BigDecimal> entry : dto.allocations().entrySet()) {
                toSave.add(FundAllocation.builder()
                        .fundCode(dto.fundCode())
                        .assetClass(entry.getKey())
                        .percentage(entry.getValue())
                        .asOfDate(date)
                        .build());
            }
        }
        allocationRepository.saveAll(toSave);
        log.info("Allocation persist: {} funds, {} rows", matched.size(), toSave.size());
        return matched.size();
    }

    private void applyInfo(Fund fund, TefasFundInfoDto info) {
        fund.setCategory(info.category());
        fund.setCategoryRank(info.categoryRank());
        fund.setCategoryTotalFunds(info.categoryFundCount());
        fund.setMarketShare(info.marketShare());
    }

    private void applyProfile(Fund fund, TefasFundProfileDto profile) {
        fund.setIsinCode(profile.isinCode());
        fund.setKapLink(profile.kapLink());
        fund.setSellValor(profile.sellValor());
        fund.setBuybackValor(profile.buybackValor());
        fund.setTradeStartTime(profile.tradeStartTime());
        fund.setTradeEndTime(profile.tradeEndTime());
        if (profile.riskValue() != null) {
            Integer risk = parseRisk(profile.riskValue());
            if (risk != null) fund.setRiskValue(risk);
        }
    }

    private Integer parseRisk(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Integer.parseInt(raw.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
