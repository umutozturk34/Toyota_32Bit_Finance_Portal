package com.finance.portfolio.service;


import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Currency;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.fixedincome.bond.BondHoldingRepository;
import com.finance.portfolio.fixedincome.deposit.DepositHoldingRepository;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.model.PortfolioType;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.shared.util.EnumParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Write-side commands for portfolios and their spot lots: create/rename/delete portfolios and
 * add/update/sell/reopen/delete positions. Every prices-in helper ({@link #toTryOnDate}) converts
 * source currency to TRY once at the relevant date's FX rate before persisting, so stored figures
 * are always TRY. Mutations publish a {@link PortfolioBackfillService.LotChangedEvent} so snapshots
 * are recomputed from the affected date. VIOP positions are rejected here and handled by the
 * dedicated derivative service.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioCrudService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final DepositHoldingRepository depositHoldingRepository;
    private final BondHoldingRepository bondHoldingRepository;
    private final TrackedAssetRepository trackedAssetRepository;
    private final PortfolioResponseMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PortfolioProperties portfolioProperties;
    private final CurrencyConverter currencyConverter;

    /** The caller's own portfolios (metadata only, no valuation); scoped by {@code userSub}. */
    @Transactional(readOnly = true)
    public List<PortfolioResponse> listPortfolios(String userSub) {
        return portfolioRepository.findByUserSub(userSub).stream()
                .map(mapper::toPortfolioResponse)
                .toList();
    }

    /**
     * Creates a portfolio, enforcing the per-user count cap and unique name.
     *
     * @throws com.finance.common.exception.BusinessException on cap reached or duplicate name
     */
    @Transactional
    public PortfolioResponse createPortfolio(String userSub, PortfolioCreateRequest request) {
        long currentCount = portfolioRepository.countByUserSub(userSub);
        int max = portfolioProperties.getMaxPortfoliosPerUser();
        if (currentCount >= max) {
            throw new BusinessException("error.portfolio.maxCountReached", max);
        }
        portfolioRepository.findByUserSubAndName(userSub, request.name())
                .ifPresent(p -> { throw new BusinessException("error.portfolio.duplicateName", request.name()); });

        Portfolio portfolio = Portfolio.builder()
                .userSub(userSub)
                .name(request.name())
                .type(request.type())
                .build();
        return mapper.toPortfolioResponse(portfolioRepository.save(portfolio));
    }

    /**
     * Renames an owned portfolio, re-checking the unique-name constraint (excluding itself).
     *
     * @throws BusinessException if another portfolio already uses the name
     */
    @Transactional
    public PortfolioResponse renamePortfolio(String userSub, Long portfolioId, PortfolioCreateRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        portfolioRepository.findByUserSubAndName(userSub, request.name())
                .filter(p -> !p.getId().equals(portfolioId))
                .ifPresent(p -> { throw new BusinessException("error.portfolio.duplicateName", request.name()); });
        portfolio.setName(request.name());
        return mapper.toPortfolioResponse(portfolioRepository.save(portfolio));
    }

    /**
     * Deletes the portfolio and cascades removal of its derivative/spot positions, fixed-income deposit
     * and bond holdings, and all snapshots. All cleanup runs in the one transaction so a failure on any
     * step rolls the whole delete back, leaving no orphaned holdings.
     */
    @Transactional
    public void deletePortfolio(String userSub, Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        derivativePositionRepository.deleteByPortfolio_Id(portfolioId);
        depositHoldingRepository.deleteByPortfolioId(portfolioId);
        bondHoldingRepository.deleteByPortfolioId(portfolioId);
        positionRepository.deleteByPortfolioId(portfolioId);
        assetSnapshotRepository.deleteByPortfolioId(portfolioId);
        dailySnapshotRepository.deleteByPortfolioId(portfolioId);
        portfolioRepository.delete(portfolio);
    }

    /**
     * Adds a spot lot, validating limits and resolving the asset against the tracked catalog. Entry
     * (and optional exit) prices are converted to TRY at their own dates before persisting; if both
     * exit fields are present the lot is created already closed.
     */
    @Transactional
    public PositionResponse addPosition(Long portfolioId, String userSub, PositionRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        // Integrity gate AFTER the ownership load: a spot lot may only land in a SPOT portfolio. Ordering matters —
        // an unowned portfolio must surface as 404 (not a type error) so we never leak that the id exists.
        portfolio.requireType(PortfolioType.SPOT);
        PortfolioValidator.validateLot(request, portfolioProperties.getLotLimits());
        AssetType assetType = EnumParser.parseOrBadRequest(AssetType.class,
                request.assetType().toUpperCase(), "enum.field.assetType");
        PortfolioValidator.validateWholeUnit(assetType, request.quantity());
        // Convert + validate the TRY price before the tracked-asset lookup: bounds are TRY, so the check
        // must run on the converted value, and doing it first fails fast on a bad price before any DB hit.
        BigDecimal entryPriceTry = toTryOnDate(request.entryPrice(), request.priceCurrency(), request.entryDate());
        PortfolioValidator.validatePriceTry(entryPriceTry, portfolioProperties.getLotLimits());
        PortfolioValidator.validateLotValueTry(entryPriceTry, request.quantity(), portfolioProperties.getLotLimits());
        TrackedAsset trackedAsset = requireTrackedAsset(assetType, request.assetCode());
        // Block opening a NEW position on an admin-disabled (retired-from-discovery) asset. Disable hides it
        // from search/cards while its detail stays readable for existing holders; letting fresh positions in
        // would re-grow exposure to a retired instrument. Only this entry point gates on enabled — existing
        // positions (add-lot, sell, recompute) are untouched, so current holders can still manage their data.
        if (!trackedAsset.isEnabled()) {
            throw new BusinessException("error.portfolio.assetDisabled", request.assetCode());
        }
        PortfolioPosition position = PortfolioPosition.builder()
                .portfolio(portfolio)
                .assetType(assetType)
                .assetCode(trackedAsset.getAssetCode())
                .quantity(request.quantity())
                .entryDate(request.entryDate())
                .entryPrice(entryPriceTry)
                .build();
        position.setTrackedAsset(trackedAsset);
        if (request.exitDate() != null && request.exitPrice() != null) {
            PortfolioValidator.validateExit(
                    request.entryDate() != null ? request.entryDate().toLocalDate() : null,
                    request.exitDate().toLocalDate());
            BigDecimal exitPriceTry = toTryOnDate(request.exitPrice(), request.priceCurrency(), request.exitDate());
            position.closeWith(request.exitDate(), exitPriceTry);
        }
        PortfolioPosition saved = positionRepository.save(position);

        publishLotChange(portfolioId, saved, saved.getEntryDate(), true);
        return mapper.toPositionResponseShell(saved);
    }

    /** Edits a lot's entry; recompute is triggered from the earlier of the old and new entry dates. */
    @Transactional
    public PositionResponse updatePosition(Long portfolioId, Long positionId, String userSub, PositionRequest request) {
        PortfolioValidator.validateLot(request, portfolioProperties.getLotLimits());
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        // Only enforce whole units when the quantity is actually being changed, so an edit that merely
        // adjusts the date/price of a legacy fractional share lot isn't blocked by the new rule.
        if (request.quantity() != null && request.quantity().compareTo(position.getQuantity()) != 0) {
            PortfolioValidator.validateWholeUnit(position.getAssetType(), request.quantity());
        }
        LocalDateTime previousEntry = position.getEntryDate();
        BigDecimal entryPriceTry = toTryOnDate(request.entryPrice(), request.priceCurrency(), request.entryDate());
        PortfolioValidator.validatePriceTry(entryPriceTry, portfolioProperties.getLotLimits());
        PortfolioValidator.validateLotValueTry(entryPriceTry, request.quantity(), portfolioProperties.getLotLimits());
        position.updateLot(request.entryDate(), entryPriceTry, request.quantity());
        PortfolioPosition saved = positionRepository.save(position);

        publishLotChange(portfolioId, saved, earliestOf(previousEntry, saved.getEntryDate()), true);
        return mapper.toPositionResponseShell(saved);
    }

    /** Removes one owned lot; drops the asset's snapshots when it was the last lot and recomputes from its entry date. */
    @Transactional
    public void deletePosition(Long portfolioId, Long positionId, String userSub) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        LocalDateTime entryDate = position.getEntryDate();
        removePositionRow(position, positionId);
        publishLotChange(portfolioId, position, entryDate, false);
    }

    /**
     * Deletes several owned lots in ONE transaction, coalescing the recompute to one event per affected
     * asset (its earliest entry date) instead of one per lot — so deleting many lots of the same asset does
     * not queue N redundant snapshot rebuilds. Missing ids are skipped (idempotent); a lot that belongs to
     * another portfolio fails the whole batch (ownership guard).
     */
    @Transactional
    public void deletePositions(Long portfolioId, List<Long> positionIds, String userSub) {
        if (positionIds == null || positionIds.isEmpty()) return;
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        // One SELECT for every requested lot; ids with no row are simply absent from the result (idempotent).
        List<PortfolioPosition> positions = positionRepository.findAllById(new LinkedHashSet<>(positionIds));
        if (positions.isEmpty()) return;
        Map<String, AssetRecompute> coalesced = new LinkedHashMap<>();
        for (PortfolioPosition position : positions) {
            if (!position.getPortfolioId().equals(portfolioId)) {
                throw new BusinessException("error.portfolio.position.notInPortfolio", portfolioId);
            }
            coalesced.merge(position.getAssetType() + "|" + position.getAssetCode(),
                    new AssetRecompute(position.getAssetType(), position.getAssetCode(), position.getEntryDate()),
                    AssetRecompute::earliest);
        }
        // Lifecycle delete (same path as single delete) so version/associations are handled correctly.
        positionRepository.deleteAll(positions);
        // Which assets still have a lot after the removals (one query, reused proven method).
        Set<String> stillHeld = positionRepository.findByPortfolioId(portfolioId).stream()
                .map(p -> p.getAssetType() + "|" + p.getAssetCode())
                .collect(Collectors.toSet());
        // Per DISTINCT asset (not per lot): drop its snapshots when no lot of it remains, then queue ONE
        // coalesced recompute from the asset's earliest deleted entry date.
        coalesced.forEach((key, a) -> {
            if (!stillHeld.contains(key)) {
                assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(portfolioId, a.assetType(), a.assetCode());
            }
            if (a.fromDate() != null) {
                eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                        portfolioId, a.assetType(), a.assetCode(), a.fromDate().toLocalDate(), false));
            }
        });
    }

    /** Deletes the lot row and drops the per-asset snapshots when it was the asset's last lot. Emits no event. */
    private void removePositionRow(PortfolioPosition position, Long positionId) {
        Long portfolioId = position.getPortfolioId();
        Long trackedAssetId = position.getTrackedAsset() != null ? position.getTrackedAsset().getId() : null;
        boolean hasOtherLot = trackedAssetId != null && positionRepository
                .existsByPortfolioIdAndTrackedAsset_IdAndIdNot(portfolioId, trackedAssetId, positionId);
        positionRepository.delete(position);
        if (!hasOtherLot) {
            assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                    portfolioId, position.getAssetType(), position.getAssetCode());
        }
    }

    /** Earliest-entry-date accumulator per asset, so a batch delete recomputes each asset once. */
    private record AssetRecompute(AssetType assetType, String assetCode, LocalDateTime fromDate) {
        AssetRecompute earliest(AssetRecompute other) {
            if (fromDate == null) return other;
            if (other.fromDate == null) return this;
            return fromDate.isBefore(other.fromDate) ? this : other;
        }
    }

    /**
     * Sells a spot lot. A full-quantity sell closes the lot in place; a partial sell carves off a new
     * closed slice for the sold quantity and shrinks the remaining open lot. Exit price is converted
     * to TRY at the exit date. VIOP is routed to the derivative close flow instead.
     */
    @Transactional
    public PositionResponse sellPosition(Long portfolioId, Long positionId, String userSub, PositionSellRequest request) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        if (position.getAssetType() == AssetType.VIOP) {
            throw new BusinessException("error.portfolio.sell.useDerivativeClose");
        }
        if (position.isClosed()) {
            throw new BusinessException("error.portfolio.sell.alreadyClosed");
        }
        PortfolioValidator.validateSell(position, request);

        BigDecimal sellQty = request.quantity();
        BigDecimal exitPriceTry = toTryOnDate(request.exitPrice(), request.priceCurrency(), request.exitDate());
        PortfolioPosition resulting;
        if (sellQty.compareTo(position.getQuantity()) == 0) {
            position.closeWith(request.exitDate(), exitPriceTry);
            resulting = positionRepository.save(position);
        } else {
            PortfolioPosition closedSlice = PortfolioPosition.builder()
                    .portfolio(position.getPortfolio())
                    .assetType(position.getAssetType())
                    .assetCode(position.getAssetCode())
                    .quantity(sellQty)
                    .entryDate(position.getEntryDate())
                    .entryPrice(position.getEntryPrice())
                    .exitDate(request.exitDate())
                    .exitPrice(exitPriceTry)
                    .build();
            closedSlice.setTrackedAsset(position.getTrackedAsset());
            positionRepository.save(closedSlice);
            position.updateLot(null, null, position.getQuantity().subtract(sellQty));
            resulting = positionRepository.save(position);
        }

        // Only valuations from the sell date forward change (the lot was held unchanged until then),
        // so backfill from exitDate — not entryDate — to avoid rebuilding the lot's entire history.
        publishLotChange(portfolioId, position, request.exitDate(), true);
        return mapper.toPositionResponseShell(resulting);
    }

    /** Reverts a closed lot to open (clears exit fields) and recomputes its history from the entry date. */
    @Transactional
    public PositionResponse reopenPosition(Long portfolioId, Long positionId, String userSub) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        if (!position.isClosed()) {
            throw new BusinessException("error.portfolio.reopen.notClosed");
        }
        position.reopen();
        PortfolioPosition saved = positionRepository.save(position);
        publishLotChange(portfolioId, position, position.getEntryDate(), true);
        return mapper.toPositionResponseShell(saved);
    }

    private PortfolioPosition loadOwnedPosition(Long portfolioId, Long positionId, String userSub) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        PortfolioPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.position.notFound", positionId));
        if (!position.getPortfolioId().equals(portfolioId)) {
            throw new BusinessException("error.portfolio.position.notInPortfolio", portfolioId);
        }
        return position;
    }

    private void publishLotChange(Long portfolioId, PortfolioPosition position, LocalDateTime fromDate, boolean visibleToUi) {
        if (fromDate == null) return;
        eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                portfolioId, position.getAssetType(), position.getAssetCode(), fromDate.toLocalDate(), visibleToUi));
    }

    private static LocalDateTime earliestOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    /** Converts a source-currency price to TRY at the given date's historical FX rate; passes through when already TRY or no currency given. */
    private BigDecimal toTryOnDate(BigDecimal price, String priceCurrency, LocalDateTime date) {
        if (price == null) return null;
        if (priceCurrency == null || priceCurrency.isBlank()) return price;
        Currency from = Currency.fromCode(priceCurrency);
        if (from == null) {
            throw new BadRequestException("error.portfolio.unsupportedCurrency", priceCurrency);
        }
        if (from == Currency.TRY) return price;
        return currencyConverter.convertAtDate(price, from, Currency.TRY, date.toLocalDate());
    }

    private TrackedAsset requireTrackedAsset(AssetType assetType, String rawCode) {
        // Safe lookup instead of TrackedAssetType.valueOf(assetType.name()): DEPOSIT/BOND have no tracked-asset
        // peer (they live in their own fixed-income tables, added via the deposit/bond services — never as a spot
        // lot), so valueOf would throw IllegalArgumentException and 500 the request. Treat the absence as
        // "not tracked here" and surface the same localized 422 as an unknown spot code.
        TrackedAssetType trackedType = assetType.trackedAssetType();
        if (trackedType == null) {
            throw new BusinessException("error.portfolio.assetNotTracked", assetType, rawCode);
        }
        String normalizedCode = trackedType.normalizeCode(rawCode);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(trackedType, normalizedCode)
                .orElseThrow(() -> new BusinessException(
                        "error.portfolio.assetNotTracked", assetType, normalizedCode));
    }
}
