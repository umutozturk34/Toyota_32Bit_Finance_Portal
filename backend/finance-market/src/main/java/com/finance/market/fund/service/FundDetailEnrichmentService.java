package com.finance.market.fund.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class FundDetailEnrichmentService {

    private final TefasClient tefasClient;
    private final FundRepository fundRepository;
    private final FundAllocationRepository allocationRepository;
    private final TrackedAssetQueryService trackedAssetQueryService;
    private final MarketCacheService<Fund> fundCacheService;
    private final FundProperties fundProperties;

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
        for (FundType type : FundType.values()) {
            try {
                LocalDate cursor = date;
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
            LocalDate cursor = date;
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

    private Set<String> loadExistingFundCodes() {
        return new HashSet<>(trackedAssetQueryService.getCodes(TrackedAssetType.FUND));
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
