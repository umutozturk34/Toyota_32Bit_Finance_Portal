package com.finance.portfolio.derivative.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.Currency;
import com.finance.market.core.service.CurrencyConverter;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.portfolio.derivative.dto.request.CloseDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.OpenDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.UpdateDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.mapper.DerivativePositionMapper;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioType;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.portfolio.service.PortfolioBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Write-side commands for VIOP derivative positions: open, close (full or partial), edit close/entry,
 * reopen, delete and auto-close at expiry. Entry/close prices supplied by the user are converted to
 * TRY at their date ({@link #toTryOnDate}); when omitted, they are resolved from historical candles
 * via {@link DerivativePriceResolver}. After any mutation the symbol's daily snapshots are rebuilt
 * and consolidated, and a backfill event is published. Note: closing always rebuilds peer snapshots
 * so lots sharing the symbol stay consistent.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DerivativePositionService {

    private final DerivativePositionRepository positionRepository;
    private final PortfolioRepository portfolioRepository;
    private final ViopContractRepository contractRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final DerivativePositionMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DerivativeSnapshotMaintenance snapshotMaintenance;
    private final DerivativePriceResolver priceResolver;
    private final CurrencyConverter currencyConverter;
    private final DerivativePositionValidator validator;

    private void publishLotChange(Long portfolioId, DerivativePosition position, LocalDate from) {
        if (from == null) return;
        String symbol = position.getViopContract() != null ? position.getViopContract().getSymbol() : null;
        if (symbol == null) return;
        eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                portfolioId, AssetType.VIOP, symbol, from, true));
    }

    /** Earliest non-null of two dates; the rebuild window must span BOTH the old and new entry so a later-moved
     *  entry still wipes the vacated days' stale daily aggregates. */
    private static LocalDate earliestOf(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    @Transactional(readOnly = true)
    public List<DerivativePositionResponse> list(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        return mapper.toResponses(positionRepository.findByPortfolioId(portfolioId));
    }

    @Transactional(readOnly = true)
    public List<DerivativePositionResponse> listOpen(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        return mapper.toResponses(positionRepository.findOpenByPortfolio(portfolioId));
    }

    /**
     * Opens a position on an active, tradeable contract whose entry date is on or before expiry. Entry
     * price is taken from the request (converted to TRY) or resolved from history; an optional close in
     * the same request creates the position already closed. Backfills and consolidates the symbol's snapshots.
     *
     * @throws BadRequestException if the contract is inactive/not tradeable, entry is after expiry,
     *                             or a required price cannot be resolved
     */
    @Transactional
    public DerivativePositionResponse open(Long portfolioId, String userSub, OpenDerivativePositionRequest request) {
        validator.requireMarketDataReady();
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userSub);
        // VIOP derivatives are spot-side product and belong only in a SPOT portfolio. Gated AFTER the ownership
        // load so an unowned portfolio still 404s ahead of this type check (no existence leak).
        portfolio.requireType(PortfolioType.SPOT);
        ViopContract contract = contractRepository.findBySymbol(request.contractSymbol())
                .orElseThrow(() -> new ResourceNotFoundException("error.viop.contractNotFound", request.contractSymbol()));
        if (!contract.isActive()) {
            throw new BadRequestException("error.viop.contractInactive", request.contractSymbol());
        }
        if (contract.getLastPrice() == null) {
            throw new BadRequestException("error.viop.contractNotTradeable", request.contractSymbol());
        }
        if (contract.getExpiryDate() != null && request.entryDate().isAfter(contract.getExpiryDate())) {
            throw new BadRequestException("error.viop.entryAfterExpiry", request.contractSymbol());
        }
        validator.requireEntryWithinViopHistory(request.entryDate());
        BigDecimal entryPrice = request.entryPrice() != null
                ? toTryOnDate(request.entryPrice(), request.priceCurrency(), request.entryDate())
                : priceResolver.resolveHistoricalPriceTry(contract, request.entryDate());
        if (entryPrice == null) {
            throw new BadRequestException("error.viop.entryPriceUnavailable", request.contractSymbol());
        }
        validator.requireNotionalWithinCap(entryPrice, contract.getContractSize(), request.quantityLot());
        DerivativePosition position = DerivativePosition.builder()
                .portfolio(portfolio)
                .viopContract(contract)
                .direction(request.direction())
                .entryDate(request.entryDate())
                .entryPrice(entryPrice)
                .quantityLot(request.quantityLot())
                .build();
        if (request.closeDate() != null) {
            BigDecimal closePrice = request.closePrice() != null
                    ? toTryOnDate(request.closePrice(), request.priceCurrency(), request.closeDate())
                    : priceResolver.resolveHistoricalPriceTry(contract, request.closeDate());
            if (closePrice == null) {
                throw new BadRequestException("error.viop.closePriceUnavailable", request.contractSymbol());
            }
            position.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
        }
        DerivativePosition saved = positionRepository.save(position);
        snapshotMaintenance.backfillSnapshots(saved);
        snapshotMaintenance.consolidateSymbolSnapshots(portfolioId, contract.getSymbol());
        publishLotChange(portfolioId, saved, saved.getEntryDate());
        log.info("DerivativePosition opened portfolio={} contract={} direction={} qty={}",
                portfolioId, contract.getSymbol(), request.direction(), request.quantityLot());
        return mapper.toResponse(saved);
    }

    /**
     * Closes an open position. A {@code closeQuantityLot} below the held quantity is a partial close
     * (splits off a closed slice, keeps the rest open); otherwise the whole position is closed. Close
     * price is from the request (to TRY) or resolved from history.
     *
     * @throws BadRequestException if already closed, close date precedes entry, close qty exceeds the
     *                             position, or the price cannot be resolved
     */
    @Transactional
    public DerivativePositionResponse close(Long positionId, Long portfolioId, String userSub,
                                            CloseDerivativePositionRequest request) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (!position.isOpen()) {
            throw new BadRequestException("error.derivative.alreadyClosed", positionId);
        }
        if (position.getEntryDate() != null && request.closeDate().isBefore(position.getEntryDate())) {
            throw new BadRequestException("error.derivative.closeBeforeEntry", positionId);
        }
        BigDecimal closePrice = request.closePrice() != null
                ? toTryOnDate(request.closePrice(), request.priceCurrency(), request.closeDate())
                : priceResolver.resolveHistoricalPriceTry(position.getViopContract(), request.closeDate());
        if (closePrice == null) {
            throw new BadRequestException("error.viop.closePriceUnavailable",
                    position.getViopContract().getSymbol());
        }

        BigDecimal closeQty = request.closeQuantityLot();
        boolean partial = closeQty != null
                && closeQty.signum() > 0
                && closeQty.compareTo(position.getQuantityLot()) < 0;
        if (closeQty != null && closeQty.compareTo(position.getQuantityLot()) > 0) {
            throw new BadRequestException("error.derivative.closeQtyExceedsPosition", positionId);
        }

        DerivativePosition primary;
        if (partial) {
            primary = splitForPartialClose(position, closeQty, request.closeDate(), closePrice);
        } else {
            position.closeFull(request.closeDate(), closePrice);
            primary = position;
        }
        String symbol = position.getViopContract().getSymbol();
        rebuildPeerSnapshots(portfolioId, symbol, primary, partial ? position : null);
        // Closing only changes valuations from the close date forward, so backfill from closeDate
        // rather than the position's entry date to avoid rebuilding the whole contract history.
        publishLotChange(portfolioId, primary, request.closeDate());
        log.info("DerivativePosition closed id={} portfolio={} closeDate={} partial={}",
                positionId, portfolioId, request.closeDate(), partial);
        return mapper.toResponse(primary);
    }

    private DerivativePosition splitForPartialClose(DerivativePosition position, BigDecimal closeQty,
                                                     LocalDate closeDate, BigDecimal closePrice) {
        DerivativePosition closedSlice = DerivativePosition.builder()
                .portfolio(position.getPortfolio())
                .viopContract(position.getViopContract())
                .direction(position.getDirection())
                .entryDate(position.getEntryDate())
                .entryPrice(position.getEntryPrice())
                .quantityLot(closeQty)
                .build();
        closedSlice.closeWith(closeDate, closePrice, DerivativeCloseReason.USER_CLOSED);
        positionRepository.save(closedSlice);
        position.reduceQuantity(closeQty);
        positionRepository.save(position);
        return closedSlice;
    }

    /**
     * Wipes and rebuilds all daily snapshots for the symbol so figures across every lot of that
     * contract stay consistent after a mutation, then re-consolidates duplicate same-timestamp rows.
     * {@code primary} is the just-changed lot; {@code reduced} is the surviving open remainder of a
     * partial close (null otherwise).
     */
    private void rebuildPeerSnapshots(Long portfolioId, String symbol, DerivativePosition primary,
                                       DerivativePosition reduced) {
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                portfolioId, AssetType.VIOP, symbol);
        snapshotMaintenance.backfillSnapshots(primary);
        if (reduced != null) snapshotMaintenance.backfillSnapshots(reduced);
        for (DerivativePosition remaining : positionRepository.findByPortfolioId(portfolioId)) {
            if (remaining.getViopContract() == null) continue;
            if (!symbol.equals(remaining.getViopContract().getSymbol())) continue;
            Long rid = remaining.getId();
            if (rid.equals(primary.getId())) continue;
            if (reduced != null && rid.equals(reduced.getId())) continue;
            snapshotMaintenance.backfillSnapshots(remaining);
        }
        snapshotMaintenance.consolidateSymbolSnapshots(portfolioId, symbol);
    }

    /** Re-closes an already-closed position with a new close date/price (reopen then close again). */
    @Transactional
    public DerivativePositionResponse updateClose(Long positionId, Long portfolioId, String userSub,
                                                   CloseDerivativePositionRequest request) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (position.isOpen()) {
            throw new BadRequestException("error.derivative.notClosed", positionId);
        }
        if (position.getEntryDate() != null && request.closeDate().isBefore(position.getEntryDate())) {
            throw new BadRequestException("error.derivative.closeBeforeEntry", positionId);
        }
        BigDecimal closePrice = request.closePrice() != null
                ? toTryOnDate(request.closePrice(), request.priceCurrency(), request.closeDate())
                : priceResolver.resolveHistoricalPriceTry(position.getViopContract(), request.closeDate());
        if (closePrice == null) {
            throw new BadRequestException("error.viop.closePriceUnavailable",
                    position.getViopContract().getSymbol());
        }
        position.reopenForUpdate();
        position.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
        rebuildPeerSnapshots(portfolioId, position.getViopContract().getSymbol(), position, null);
        log.info("DerivativePosition close updated id={} portfolio={} newCloseDate={}",
                positionId, portfolioId, request.closeDate());
        return mapper.toResponse(position);
    }

    /** Edits an open position's direction/entry date/price/lots, re-validating against contract expiry. */
    @Transactional
    public DerivativePositionResponse updateOpen(Long positionId, Long portfolioId, String userSub,
                                                  UpdateDerivativePositionRequest request) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (!position.isOpen()) {
            throw new BadRequestException("error.derivative.alreadyClosed", positionId);
        }
        ViopContract contract = position.getViopContract();
        if (contract.getExpiryDate() != null && request.entryDate().isAfter(contract.getExpiryDate())) {
            throw new BadRequestException("error.viop.entryAfterExpiry", contract.getSymbol());
        }
        validator.requireEntryWithinViopHistory(request.entryDate());
        BigDecimal entryPrice = request.entryPrice() != null
                ? toTryOnDate(request.entryPrice(), request.priceCurrency(), request.entryDate())
                : priceResolver.resolveHistoricalPriceTry(contract, request.entryDate());
        if (entryPrice == null) {
            throw new BadRequestException("error.viop.entryPriceUnavailable", contract.getSymbol());
        }
        validator.requireNotionalWithinCap(entryPrice, contract.getContractSize(), request.quantityLot());
        LocalDate previousEntry = position.getEntryDate();
        position.updateEntry(request.direction(), request.entryDate(), entryPrice, request.quantityLot());
        rebuildPeerSnapshots(portfolioId, contract.getSymbol(), position, null);
        // Rebuild back to the EARLIER of old/new entry: moving the entry LATER vacates the old days, whose stale
        // daily aggregates must also be wiped (the TRY chart reads stored snapshots, so a new-date-only fromDate
        // left a phantom step at the old entry). Mirrors PortfolioCrudService.updatePosition's earliestOf for spot.
        publishLotChange(portfolioId, position, earliestOf(previousEntry, position.getEntryDate()));
        log.info("DerivativePosition entry updated id={} portfolio={} entryDate={} qty={}",
                positionId, portfolioId, request.entryDate(), request.quantityLot());
        return mapper.toResponse(position);
    }

    @Transactional
    public DerivativePositionResponse reopen(Long positionId, Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (position.isOpen()) {
            throw new BadRequestException("error.derivative.notClosed", positionId);
        }
        position.reopenForUpdate();
        rebuildPeerSnapshots(portfolioId, position.getViopContract().getSymbol(), position, null);
        publishLotChange(portfolioId, position, position.getEntryDate());
        log.info("DerivativePosition reopened id={} portfolio={}", positionId, portfolioId);
        return mapper.toResponse(position);
    }

    @Transactional
    public void delete(Long positionId, Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        String symbol = position.getViopContract() != null ? position.getViopContract().getSymbol() : null;
        LocalDate fromDate = position.getEntryDate();
        positionRepository.delete(position);
        if (symbol != null) {
            assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                    portfolioId, AssetType.VIOP, symbol);
            for (DerivativePosition remaining : positionRepository.findByPortfolioId(portfolioId)) {
                if (remaining.getViopContract() != null
                        && symbol.equals(remaining.getViopContract().getSymbol())) {
                    snapshotMaintenance.backfillSnapshots(remaining);
                }
            }
            snapshotMaintenance.consolidateSymbolSnapshots(portfolioId, symbol);
            if (fromDate != null) {
                eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                        portfolioId, AssetType.VIOP, symbol, fromDate, true));
            }
        }
        log.info("DerivativePosition deleted id={} portfolio={}", positionId, portfolioId);
    }

    /**
     * Deletes several owned derivative positions in ONE transaction, coalescing snapshot rebuilds to one
     * pass per affected VIOP symbol (its earliest entry date) instead of one per position. Missing ids are
     * skipped (idempotent).
     */
    @Transactional
    public void deleteAll(List<Long> positionIds, Long portfolioId, String userSub) {
        if (positionIds == null || positionIds.isEmpty()) return;
        requireOwnedPortfolio(portfolioId, userSub);
        Map<String, LocalDate> earliestBySymbol = new LinkedHashMap<>();
        List<DerivativePosition> toDelete = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(positionIds)) {
            DerivativePosition position = positionRepository.findByIdAndPortfolioId(id, portfolioId).orElse(null);
            if (position == null) continue;
            String symbol = position.getViopContract() != null ? position.getViopContract().getSymbol() : null;
            LocalDate fromDate = position.getEntryDate();
            toDelete.add(position);
            if (symbol != null) {
                if (fromDate == null) earliestBySymbol.putIfAbsent(symbol, null);
                else earliestBySymbol.merge(symbol, fromDate, (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        if (toDelete.isEmpty()) return;
        positionRepository.deleteAll(toDelete);
        // Remaining positions grouped by symbol, loaded ONCE (was: re-query the whole portfolio per symbol).
        Map<String, List<DerivativePosition>> remainingBySymbol = positionRepository.findByPortfolioId(portfolioId).stream()
                .filter(p -> p.getViopContract() != null)
                .collect(Collectors.groupingBy(p -> p.getViopContract().getSymbol()));
        for (Map.Entry<String, LocalDate> e : earliestBySymbol.entrySet()) {
            String symbol = e.getKey();
            assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(portfolioId, AssetType.VIOP, symbol);
            for (DerivativePosition remaining : remainingBySymbol.getOrDefault(symbol, List.of())) {
                snapshotMaintenance.backfillSnapshots(remaining);
            }
            snapshotMaintenance.consolidateSymbolSnapshots(portfolioId, symbol);
            if (e.getValue() != null) {
                eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                        portfolioId, AssetType.VIOP, symbol, e.getValue(), true));
            }
        }
        log.info("DerivativePositions bulk-deleted count={} portfolio={}", toDelete.size(), portfolioId);
    }

    /**
     * Force-closes any still-open positions whose contract has expired, using the contract's settlement
     * price (falling back to last price), converted to TRY at expiry. Skips positions with no price.
     * <p>
     * This is correct for OPTIONS too, and does NOT need a separate {@code max(0, spot - strike)} intrinsic
     * branch: a VIOP contract is quoted by its own price (an option by its premium, not the underlying spot),
     * so {@code settlementPrice}/{@code lastPrice} here is the option's settlement PREMIUM — which the exchange
     * already fixes to the option's intrinsic value at expiry (0 when out-of-the-money). Closing at that premium
     * therefore yields the right P&L: an in-the-money option realises (intrinsic − premium paid), an OTM one
     * loses exactly the premium. Subtracting the strike from this premium-basis number would corrupt the close.
     *
     * @return the number of positions auto-closed
     */
    @Transactional
    public int autoCloseExpired() {
        List<DerivativePosition> orphaned = positionRepository.findOpenWithExpiredContract(LocalDate.now());
        int closed = 0;
        int skippedNoPrice = 0;
        int skippedNoFx = 0;
        for (DerivativePosition pos : orphaned) {
            ViopContract contract = pos.getViopContract();
            BigDecimal settlementNative = contract.getSettlementPrice() != null
                    ? contract.getSettlementPrice()
                    : contract.getLastPrice();
            if (settlementNative == null) {
                skippedNoPrice++;
                continue;
            }
            BigDecimal settlementTry = priceResolver.nativeToTryOnDate(settlementNative,
                    contract.resolvePriceCurrency(), contract.getExpiryDate());
            // null means non-TRY contract with no historical FX rate available. Closing with a
            // raw foreign-currency number would persist garbage as TRY (the bug we hit on USD
            // futures during FX outages). Skip and retry next scheduler tick — better to leave
            // the position open than corrupt close_price.
            if (settlementTry == null) {
                log.warn("Skipping autoClose for position={} contract={} — settlement FX unavailable on expiry {}",
                        pos.getId(), contract.getSymbol(), contract.getExpiryDate());
                skippedNoFx++;
                continue;
            }
            pos.closeWith(contract.getExpiryDate(), settlementTry, DerivativeCloseReason.EXPIRED);
            rebuildPeerSnapshots(pos.getPortfolio().getId(), contract.getSymbol(), pos, null);
            closed++;
        }
        if (closed > 0 || skippedNoPrice > 0 || skippedNoFx > 0) {
            log.info("autoCloseExpired closed={} skippedNoPrice={} skippedNoFx={}",
                    closed, skippedNoPrice, skippedNoFx);
        }
        return closed;
    }

    private BigDecimal toTryOnDate(BigDecimal price, String priceCurrency, LocalDate date) {
        if (price == null) return null;
        if (priceCurrency == null || priceCurrency.isBlank()) return price;
        Currency from = Currency.fromCode(priceCurrency);
        if (from == null) {
            throw new BadRequestException("error.portfolio.unsupportedCurrency", priceCurrency);
        }
        if (from == Currency.TRY) return price;
        return currencyConverter.convertAtDate(price, from, Currency.TRY, date);
    }

    private Portfolio requireOwnedPortfolio(Long portfolioId, String userSub) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        if (!portfolio.getUserSub().equals(userSub)) {
            throw new ResourceNotFoundException("error.portfolio.notFound", portfolioId);
        }
        return portfolio;
    }
}
