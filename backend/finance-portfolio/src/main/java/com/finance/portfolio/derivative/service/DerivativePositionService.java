package com.finance.portfolio.derivative.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.viop.dto.ViopHistoryPoint;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopHistoryResolution;
import com.finance.market.viop.port.ViopMarketDataPort;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.market.viop.service.ViopHistoryProvider;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.common.model.MarketType;
import com.finance.portfolio.derivative.dto.request.CloseDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.OpenDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.UpdateDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.mapper.DerivativePositionMapper;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioAssetDailySnapshot;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.portfolio.service.SnapshotCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class DerivativePositionService {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final int HISTORY_LOOKBACK_DAYS = 10;

    private final DerivativePositionRepository positionRepository;
    private final PortfolioRepository portfolioRepository;
    private final ViopContractRepository contractRepository;
    private final ViopCandleRepository candleRepository;
    private final ViopHistoryProvider historyProvider;
    private final HistoricalPricingPort historicalPricingPort;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final SnapshotCalculationService snapshotCalculator;
    private final ViopMarketDataPort viopMarketData;
    private final DerivativePositionMapper mapper;

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

    @Transactional
    public DerivativePositionResponse open(Long portfolioId, String userSub, OpenDerivativePositionRequest request) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userSub);
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
        BigDecimal entryPrice = request.entryPrice() != null
                ? request.entryPrice()
                : resolveHistoricalPriceTry(contract, request.entryDate());
        if (entryPrice == null) {
            throw new BadRequestException("error.viop.entryPriceUnavailable", request.contractSymbol());
        }
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
                    ? request.closePrice()
                    : resolveHistoricalPriceTry(contract, request.closeDate());
            if (closePrice == null) {
                throw new BadRequestException("error.viop.closePriceUnavailable", request.contractSymbol());
            }
            position.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
        }
        DerivativePosition saved = positionRepository.save(position);
        backfillSnapshots(saved);
        log.info("DerivativePosition opened portfolio={} contract={} direction={} qty={}",
                portfolioId, contract.getSymbol(), request.direction(), request.quantityLot());
        return mapper.toResponse(saved);
    }

    /**
     * Persists one {@code portfolio_asset_daily_snapshots} row per trading day between
     * {@code entryDate} and the position's effective end date (close date or today). Reads the
     * close price for each date from {@code viop_candles}; if candle history is sparse for the
     * contract it is fetched on-demand by {@link ViopHistoryProvider#fetchAndPersist}. This is
     * the persistence path that replaces the previous read-time
     * {@code DerivativePerformanceContributor} augmentation — once backfill completes the
     * aggregate snapshot path is self-sufficient.
     */
    private void backfillSnapshots(DerivativePosition position) {
        if (position.getViopContract() == null) return;
        String symbol = position.getViopContract().getSymbol();
        LocalDate from = position.getEntryDate();
        LocalDate to = position.getCloseDate() != null ? position.getCloseDate() : LocalDate.now();
        if (from == null || from.isAfter(to)) return;

        historyProvider.fetchAndPersist(symbol, from, to);

        LocalDateTime fromDT = from.atStartOfDay();
        LocalDateTime toDT = to.plusDays(1).atStartOfDay().minusSeconds(1);
        Map<LocalDate, BigDecimal> closeByDate = new HashMap<>();
        for (ViopCandle candle : candleRepository
                .findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(symbol, fromDT, toDT)) {
            if (candle.getCandleDate() != null && candle.getClose() != null) {
                closeByDate.put(candle.getCandleDate().toLocalDate(), candle.getClose());
            }
        }
        if (closeByDate.isEmpty()) return;

        // Per-date FX series — same rule as asset detail chart. For USD/EUR-quoted contracts the
        // snapshot for day D uses USD/TRY (or EUR/TRY) AS OF day D, not today's rate. Falls back
        // to the nearest prior rate if a specific date isn't in the forex history.
        String currency = position.getViopContract().getCurrency();
        boolean needsFxConversion = currency != null && !"TRY".equalsIgnoreCase(currency);
        Map<LocalDate, BigDecimal> fxByDate = needsFxConversion
                ? historicalPricingPort.getPriceSeries(MarketType.FOREX, currency.toUpperCase(),
                        from.minusDays(7), to)
                : Map.of();

        // Seed lastKnown with NATIVE entry-equivalent so weekend/holiday fallbacks (when no candle
        // exists for the date) stay in the native unit and per-date FX × can convert to TRY
        // cleanly. entry_price is TRY now, so divide by entry-date FX to get the native baseline.
        BigDecimal entryFxAtOpen = needsFxConversion
                ? Optional.ofNullable(closestPriorRate(fxByDate, from)).orElse(BigDecimal.ONE)
                : BigDecimal.ONE;
        BigDecimal lastKnown = needsFxConversion && position.getEntryPrice() != null && entryFxAtOpen.signum() > 0
                ? position.getEntryPrice().divide(entryFxAtOpen, 8, java.math.RoundingMode.HALF_UP)
                : position.getEntryPrice();
        BigDecimal lastFxRate = BigDecimal.ONE;
        LocalDate closeDate = position.getCloseDate();
        BigDecimal closePriceOverride = position.getClosePrice();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean useCloseOverride = closeDate != null && date.equals(closeDate) && closePriceOverride != null;
            BigDecimal close = useCloseOverride
                    ? closePriceOverride
                    : closeByDate.getOrDefault(date, lastKnown);
            if (close == null) continue;
            if (!useCloseOverride) lastKnown = close;
            BigDecimal fxRate;
            // closePriceOverride and entry_price are TRY-canonical in DB; candle close is native.
            // Apply FX only to native candle closes — for TRY-canonical override use 1.0 so the
            // snapshot doesn't double-convert.
            if (useCloseOverride || !needsFxConversion) {
                fxRate = BigDecimal.ONE;
            } else {
                BigDecimal byDay = closestPriorRate(fxByDate, date);
                fxRate = byDay != null && byDay.signum() > 0 ? byDay : lastFxRate;
                lastFxRate = fxRate;
            }
            LocalDateTime ts = date.atTime(LocalTime.NOON);
            PortfolioAssetDailySnapshot snapshot = snapshotCalculator
                    .buildDerivativeAssetSnapshotAt(position.getPortfolio().getId(), position, ts, close, fxRate);
            if (snapshot != null) {
                assetSnapshotRepository.save(snapshot);
            }
        }
    }

    private static BigDecimal closestPriorRate(Map<LocalDate, BigDecimal> series, LocalDate target) {
        if (series == null || series.isEmpty()) return null;
        LocalDate cursor = target;
        for (int i = 0; i <= 30; i++) {
            BigDecimal rate = series.get(cursor);
            if (rate != null) return rate;
            cursor = cursor.minusDays(1);
        }
        return null;
    }

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
                ? request.closePrice()
                : resolveHistoricalPriceTry(position.getViopContract(), request.closeDate());
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
            DerivativePosition closedSlice = DerivativePosition.builder()
                    .portfolio(position.getPortfolio())
                    .viopContract(position.getViopContract())
                    .direction(position.getDirection())
                    .entryDate(position.getEntryDate())
                    .entryPrice(position.getEntryPrice())
                    .quantityLot(closeQty)
                    .build();
            closedSlice.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
            positionRepository.save(closedSlice);
            position.reduceQuantity(closeQty);
            positionRepository.save(position);
            primary = closedSlice;
        } else {
            position.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
            primary = position;
        }
        String symbol = position.getViopContract().getSymbol();
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                portfolioId, com.finance.portfolio.model.AssetType.VIOP, symbol);
        backfillSnapshots(primary);
        if (partial) backfillSnapshots(position);
        for (DerivativePosition remaining : positionRepository.findOpenByPortfolio(portfolioId)) {
            if (remaining.getViopContract() != null
                    && symbol.equals(remaining.getViopContract().getSymbol())
                    && !remaining.getId().equals(position.getId())) {
                backfillSnapshots(remaining);
            }
        }
        log.info("DerivativePosition closed id={} portfolio={} closeDate={} partial={}",
                positionId, portfolioId, request.closeDate(), partial);
        return mapper.toResponse(primary);
    }

    /**
     * Updates an already-closed position's close date/price retroactively. User decides they
     * closed earlier or at a different price; we wipe the symbol's snapshot history and
     * rebuild entry → new closeDate so portfolio totals/charts reflect the corrected values.
     */
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
                ? request.closePrice()
                : resolveHistoricalPriceTry(position.getViopContract(), request.closeDate());
        if (closePrice == null) {
            throw new BadRequestException("error.viop.closePriceUnavailable",
                    position.getViopContract().getSymbol());
        }
        position.reopenForUpdate();
        position.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
        String symbol = position.getViopContract().getSymbol();
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                portfolioId, com.finance.portfolio.model.AssetType.VIOP, symbol);
        backfillSnapshots(position);
        for (DerivativePosition remaining : positionRepository.findByPortfolioId(portfolioId)) {
            if (remaining.getId().equals(position.getId())) continue;
            if (remaining.getViopContract() != null
                    && symbol.equals(remaining.getViopContract().getSymbol())) {
                backfillSnapshots(remaining);
            }
        }
        log.info("DerivativePosition close updated id={} portfolio={} newCloseDate={}",
                positionId, portfolioId, request.closeDate());
        return mapper.toResponse(position);
    }

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
        BigDecimal entryPrice = request.entryPrice() != null
                ? request.entryPrice()
                : resolveHistoricalPriceTry(contract, request.entryDate());
        if (entryPrice == null) {
            throw new BadRequestException("error.viop.entryPriceUnavailable", contract.getSymbol());
        }
        position.updateEntry(request.direction(), request.entryDate(), entryPrice, request.quantityLot());
        String symbol = contract.getSymbol();
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                portfolioId, com.finance.portfolio.model.AssetType.VIOP, symbol);
        backfillSnapshots(position);
        for (DerivativePosition remaining : positionRepository.findByPortfolioId(portfolioId)) {
            if (remaining.getId().equals(position.getId())) continue;
            if (remaining.getViopContract() != null
                    && symbol.equals(remaining.getViopContract().getSymbol())) {
                backfillSnapshots(remaining);
            }
        }
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
        String symbol = position.getViopContract().getSymbol();
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                portfolioId, com.finance.portfolio.model.AssetType.VIOP, symbol);
        backfillSnapshots(position);
        for (DerivativePosition remaining : positionRepository.findByPortfolioId(portfolioId)) {
            if (remaining.getId().equals(position.getId())) continue;
            if (remaining.getViopContract() != null
                    && symbol.equals(remaining.getViopContract().getSymbol())) {
                backfillSnapshots(remaining);
            }
        }
        log.info("DerivativePosition reopened id={} portfolio={}", positionId, portfolioId);
        return mapper.toResponse(position);
    }

    @Transactional
    public void delete(Long positionId, Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        String symbol = position.getViopContract() != null ? position.getViopContract().getSymbol() : null;
        positionRepository.delete(position);
        if (symbol != null) {
            assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                    portfolioId, com.finance.portfolio.model.AssetType.VIOP, symbol);
            for (DerivativePosition remaining : positionRepository.findOpenByPortfolio(portfolioId)) {
                if (remaining.getViopContract() != null
                        && symbol.equals(remaining.getViopContract().getSymbol())) {
                    backfillSnapshots(remaining);
                }
            }
        }
        log.info("DerivativePosition deleted id={} portfolio={}", positionId, portfolioId);
    }

    @Transactional
    public int autoCloseExpired() {
        List<DerivativePosition> orphaned = positionRepository.findOpenWithExpiredContract(java.time.LocalDate.now());
        int closed = 0;
        for (DerivativePosition pos : orphaned) {
            ViopContract contract = pos.getViopContract();
            BigDecimal settlementNative = contract.getSettlementPrice() != null
                    ? contract.getSettlementPrice()
                    : contract.getLastPrice();
            if (settlementNative == null) continue;
            BigDecimal settlementTry = nativeToTryOnDate(settlementNative,
                    contract.getCurrency(), contract.getExpiryDate());
            pos.closeWith(contract.getExpiryDate(), settlementTry, DerivativeCloseReason.EXPIRED);
            String symbol = contract.getSymbol();
            assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                    pos.getPortfolio().getId(), com.finance.portfolio.model.AssetType.VIOP, symbol);
            backfillSnapshots(pos);
            for (DerivativePosition remaining : positionRepository.findOpenByPortfolio(pos.getPortfolio().getId())) {
                if (remaining.getViopContract() != null
                        && symbol.equals(remaining.getViopContract().getSymbol())) {
                    backfillSnapshots(remaining);
                }
            }
            closed++;
        }
        if (closed > 0) {
            log.info("Auto-closed {} expired derivative positions", closed);
        }
        return closed;
    }

    private Portfolio requireOwnedPortfolio(Long portfolioId, String userSub) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        if (!portfolio.getUserSub().equals(userSub)) {
            throw new ResourceNotFoundException("error.portfolio.notFound", portfolioId);
        }
        return portfolio;
    }

    /**
     * Returns the contract's historical close on {@code date} converted to TRY using the
     * USD/TRY (or EUR/TRY) rate AS OF that date. Used when frontend doesn't supply explicit
     * entry/close price — backend resolves from upstream/candles and converts to TRY so the
     * DB column stays TRY-canonical.
     */
    private BigDecimal resolveHistoricalPriceTry(ViopContract contract, java.time.LocalDate date) {
        BigDecimal nativePrice = resolveHistoricalPrice(contract, date);
        if (nativePrice == null) return null;
        return nativeToTryOnDate(nativePrice, contract.getCurrency(), date);
    }

    private BigDecimal nativeToTryOnDate(BigDecimal nativePrice, String currency, java.time.LocalDate date) {
        if (nativePrice == null) return null;
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) return nativePrice;
        Map<java.time.LocalDate, BigDecimal> fxSeries = historicalPricingPort.getPriceSeries(
                MarketType.FOREX, currency.toUpperCase(), date.minusDays(7), date);
        BigDecimal rate = closestPriorRate(fxSeries, date);
        return rate != null && rate.signum() > 0 ? nativePrice.multiply(rate) : nativePrice;
    }

    private BigDecimal resolveHistoricalPrice(ViopContract contract, java.time.LocalDate date) {
        Instant from = date.minusDays(HISTORY_LOOKBACK_DAYS).atStartOfDay(ISTANBUL).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ISTANBUL).minus(1, ChronoUnit.SECONDS).toInstant();
        List<ViopHistoryPoint> points = viopMarketData.fetchHistory(
                contract.getSymbol(), ViopHistoryResolution.DAILY, from, to);
        if (points.isEmpty()) return null;
        java.time.LocalDateTime requestedEnd = date.plusDays(1).atStartOfDay();
        ViopHistoryPoint closest = null;
        for (ViopHistoryPoint point : points) {
            if (point.candleDate() != null && point.candleDate().isBefore(requestedEnd)) {
                closest = point;
            }
        }
        return closest != null ? closest.close() : null;
    }
}
