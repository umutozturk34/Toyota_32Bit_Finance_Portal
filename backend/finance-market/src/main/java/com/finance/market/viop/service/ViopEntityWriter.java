package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.core.service.MarketEntityWriter;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.repository.ViopContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persists VIOP contracts from specs and live snapshots. Unknown symbols are bootstrapped by
 * parsing the symbol (and auto-tracked); snapshot fields are applied only when present so partial
 * upstream payloads never wipe existing data. Saved contracts are pushed to the snapshot cache.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class ViopEntityWriter implements MarketEntityWriter {

    private static final int PRICE_SCALE = 4;
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final ViopContractRepository repository;
    private final AssetRegistryService assetRegistry;
    private final TrackedAssetCommandService trackedAssetCommand;
    private final ViopCategoryResolver categoryResolver;
    private final ViopSymbolParser symbolParser;
    private final MarketCacheService<ViopContract> cacheService;

    /** Applies contract specs onto existing contracts; symbols not yet stored are skipped. */
    @Transactional
    public int enrichSpecs(List<ViopContractSpec> specs) {
        int enriched = 0;
        for (ViopContractSpec spec : specs) {
            ViopContract entity = repository.findBySymbol(spec.symbol()).orElse(null);
            if (entity == null) {
                continue;
            }
            applySpec(entity, spec);
            entity.scaleFields(PRICE_SCALE);
            repository.save(entity);
            enriched++;
        }
        return enriched;
    }

    /**
     * Fills in option contracts' contract size (and settlement/currency/margin) from the option metadata
     * templates. Templates are per-UNDERLYING families (a sample code, not a concrete strike), so they are
     * keyed by underlying and applied to every stored option of that underlying that still lacks a size.
     * Without this, options keep a null contract size that valuation reads as 1 — understating notional and
     * P&L by the real multiplier (e.g. 100 for equity options) and hiding the {@code ×N} badge.
     */
    @Transactional
    public int enrichOptionContractSizes(List<ViopContractSpec> templates) {
        if (templates == null || templates.isEmpty()) return 0;
        Map<String, ViopContractSpec> byUnderlying = new HashMap<>();
        for (ViopContractSpec t : templates) {
            String key = normalizeUnderlying(t.underlying());
            if (key != null && t.contractSize() != null) {
                byUnderlying.putIfAbsent(key, t);
            }
        }
        if (byUnderlying.isEmpty()) return 0;
        int enriched = 0;
        for (ViopContract option : repository.findByKindAndActiveTrue(ViopContractKind.OPTION)) {
            if (option.getContractSize() != null) continue;
            ViopContractSpec t = byUnderlying.get(normalizeUnderlying(option.getUnderlying()));
            if (t == null) continue;
            option.setContractSize(t.contractSize());
            if (option.getSettlementType() == null) option.setSettlementType(t.settlementType());
            if (option.getCurrency() == null) option.setCurrency(t.currency());
            if (option.getInitialMargin() == null && t.initialMargin() != null) option.setInitialMargin(t.initialMargin());
            option.scaleFields(PRICE_SCALE);
            repository.save(option);
            cacheService.putSnapshot(option.getSymbol(), option);
            enriched++;
        }
        return enriched;
    }

    /** Upper-cases and trims an underlying for cross-source matching; {@code null}/blank stays null. */
    private static String normalizeUnderlying(String underlying) {
        if (underlying == null || underlying.isBlank()) return null;
        return underlying.trim().toUpperCase();
    }

    /** Applies a batch of live snapshots (bootstrapping new symbols); returns the symbols touched. */
    @Transactional
    public Set<String> applyBulkSnapshots(List<ViopQuoteSnapshot> snapshots) {
        Set<String> seen = new HashSet<>();
        if (snapshots == null || snapshots.isEmpty()) return seen;
        Map<String, ViopContract> existing = repository.findAll().stream()
                .collect(Collectors.toMap(ViopContract::getSymbol, Function.identity()));
        int priceChanged = 0;
        int bootstrapped = 0;
        for (ViopQuoteSnapshot snap : snapshots) {
            ViopContract tracked = existing.get(snap.symbol());
            boolean isNew = tracked == null;
            ViopContract entity = isNew ? bootstrapFromSymbol(snap.symbol()) : tracked;
            // Null only when a brand-new symbol failed to bootstrap; skip it (existing contracts are never null).
            if (entity == null) continue;
            if (isNew) bootstrapped++;
            BigDecimal priorLast = entity.getLastPrice();
            BigDecimal priorClose = entity.getDayClose();
            entity.setActive(true);
            applySnapshot(entity, snap);
            entity.scaleFields(PRICE_SCALE);
            ViopContract saved = repository.save(entity);
            cacheService.putSnapshot(saved.getSymbol(), saved);
            seen.add(snap.symbol());
            if (isNew || !valuesEqual(priorLast, saved.getLastPrice())
                    || !valuesEqual(priorClose, saved.getDayClose())) {
                priceChanged++;
            }
        }
        log.info("VIOP snapshots: received={} priceChanged={} newContracts={} unchanged={}",
                seen.size(), priceChanged, bootstrapped, seen.size() - priceChanged);
        return seen;
    }

    private static boolean valuesEqual(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    /** Marks every currently-active contract absent from {@code activeSymbols} as inactive. */
    @Transactional
    public int deactivateNotIn(Set<String> activeSymbols) {
        List<ViopContract> all = repository.findAll();
        int deactivated = 0;
        for (ViopContract c : all) {
            if (c.isActive() && !activeSymbols.contains(c.getSymbol())) {
                c.setActive(false);
                deactivated++;
            }
        }
        if (deactivated > 0) {
            repository.saveAll(all);
        }
        return deactivated;
    }

    /** Applies one snapshot, bootstrapping the contract if unknown; throws if the symbol is unparseable. */
    @Transactional
    public ViopContract applySnapshot(String symbol, ViopQuoteSnapshot snap) {
        ViopContract entity = repository.findBySymbol(symbol)
                .orElseGet(() -> Optional.ofNullable(bootstrapFromSymbol(symbol))
                        .orElseThrow(() -> new IllegalStateException("Unable to bootstrap ViopContract for " + symbol)));
        applySnapshot(entity, snap);
        entity.scaleFields(PRICE_SCALE);
        ViopContract saved = repository.save(entity);
        cacheService.putSnapshot(saved.getSymbol(), saved);
        return saved;
    }

    @Transactional
    public int markExpired(LocalDate today) {
        List<ViopContract> expired = repository.findExpired(today);
        for (ViopContract c : expired) {
            c.setActive(false);
        }
        if (!expired.isEmpty()) {
            log.info("Marked {} VIOP contracts as expired", expired.size());
            repository.saveAll(expired);
        }
        return expired.size();
    }

    /** Creates a contract purely from its parsed symbol, registering and auto-tracking it. */
    private ViopContract bootstrapFromSymbol(String symbol) {
        ViopSymbolParser.Parsed parsed = symbolParser.parse(symbol);
        if (parsed == null) {
            log.warn("Skipping unrecognised VIOP symbol: {}", symbol);
            return null;
        }
        ViopContract entity = ViopContract.builder()
                .symbol(symbol)
                .kind(parsed.kind())
                .underlying(parsed.underlying())
                .expiryDate(symbolParser.impliedExpiry(parsed.expiryYear(), parsed.expiryMonth()))
                .optionSide(parsed.optionSide())
                .strikePrice(parsed.strikePrice())
                .exerciseStyle(parsed.exerciseStyle())
                .active(true)
                .build();
        entity.setCategory(categoryResolver.resolve(parsed.kind(), parsed.underlying(), null));
        entity.setName(symbol);
        entity.setDisplayName(ViopDisplayNameBuilder.build(parsed.kind(), parsed.underlying(),
                parsed.optionSide(), parsed.strikePrice(),
                symbolParser.impliedExpiry(parsed.expiryYear(), parsed.expiryMonth())));
        entity.setAsset(assetRegistry.upsert(MarketType.VIOP, symbol));
        trackedAssetCommand.autoTrack(TrackedAssetType.VIOP, symbol, entity.getName(), 0);
        return entity;
    }

    private void applySpec(ViopContract entity, ViopContractSpec spec) {
        entity.setKind(spec.kind());
        entity.setUnderlying(spec.underlying());
        entity.setExpiryDate(spec.expiryDate());
        entity.setContractSize(spec.contractSize());
        entity.setInitialMargin(spec.initialMargin());
        entity.setSettlementType(spec.settlementType());
        entity.setCurrency(spec.currency());
        entity.setOptionSide(spec.optionSide());
        entity.setStrikePrice(spec.strikePrice());
        entity.setCategory(categoryResolver.resolve(spec.kind(), spec.underlying(), spec.currency()));
        if (entity.getName() == null || entity.getName().isBlank()) {
            entity.setName(spec.displayName() != null ? spec.displayName() : spec.symbol());
        }
        entity.setDisplayName(ViopDisplayNameBuilder.build(spec.kind(), spec.underlying(),
                spec.optionSide(), spec.strikePrice(), spec.expiryDate()));
        if (entity.isActive() && spec.expiryDate() != null && spec.expiryDate().isBefore(LocalDate.now())) {
            entity.setActive(false);
        }
    }

    /** Copies present snapshot fields onto the entity and recomputes change vs. day close. */
    private void applySnapshot(ViopContract entity, ViopQuoteSnapshot snap) {
        backfillDisplayName(entity);
        if (snap.dayClose() != null) entity.setDayClose(snap.dayClose());
        if (snap.bid() != null) entity.setBid(snap.bid());
        if (snap.ask() != null) entity.setAsk(snap.ask());
        if (snap.open() != null) entity.setOpenPrice(snap.open());
        if (snap.high() != null) entity.setDayHigh(snap.high());
        if (snap.low() != null) entity.setDayLow(snap.low());
        if (snap.volumeLot() != null) entity.setVolumeLot(snap.volumeLot());
        if (snap.volumeTry() != null) entity.setVolumeTry(snap.volumeTry());
        if (snap.settlement() != null) entity.setSettlementPrice(snap.settlement());
        if (snap.initialMargin() != null) entity.setInitialMargin(snap.initialMargin());
        if (snap.priceStep() != null) entity.setTickSize(snap.priceStep());

        BigDecimal effectiveLast = firstPositive(snap.last(), snap.dayClose(),
                snap.settlement(), snap.preSettlement());
        if (effectiveLast != null) entity.setLastPrice(effectiveLast);

        Instant ts = snap.updatedAt() != null ? snap.updatedAt() : Instant.now();
        entity.setLastUpdated(LocalDateTime.ofInstant(ts, ISTANBUL));
        entity.applyChange(entity.getLastPrice(), entity.getDayClose(), PRICE_SCALE);
    }

    private void backfillDisplayName(ViopContract entity) {
        if (entity.getDisplayName() != null && !entity.getDisplayName().isBlank()) return;
        String name = ViopDisplayNameBuilder.build(entity.getKind(), entity.getUnderlying(),
                entity.getOptionSide(), entity.getStrikePrice(), entity.getExpiryDate());
        if (name != null && !name.isBlank()) {
            entity.setDisplayName(name);
        }
    }

    /** First strictly-positive candidate, used to pick a usable last price among fallbacks. */
    private static BigDecimal firstPositive(BigDecimal... candidates) {
        for (BigDecimal candidate : candidates) {
            if (candidate != null && candidate.signum() > 0) {
                return candidate;
            }
        }
        return null;
    }
}
