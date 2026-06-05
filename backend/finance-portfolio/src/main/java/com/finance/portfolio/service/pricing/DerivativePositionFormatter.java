package com.finance.portfolio.service.pricing;

import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.dto.response.DerivativeMeta;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Maps a {@link DerivativePosition} into the unified {@link PositionResponse} shown in the positions
 * grid. Values it live in TRY (notional, market value, PnL%), builds a display name, and packs
 * derivative-specific facts (kind, margin, expiry, strike, option max loss/gain) into {@link DerivativeMeta}.
 * Current price comes from the latest candle close (or contract last price) converted to TRY; closed
 * positions use the stored close price.
 */
@Component
@RequiredArgsConstructor
public class DerivativePositionFormatter {

    private static final String OPTION_KIND = "OPTION";
    private static final String LONG_DIRECTION = "LONG";
    private static final String CLOSED_SUFFIX = " · KAPALI";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DerivativePricingResolver pricingResolver;

    public PositionResponse toPositionResponse(DerivativePosition position) {
        if (position.getViopContract() == null) return null;
        DerivativeFigures f = computeFigures(position);
        DerivativeMeta meta = buildMeta(position, f);
        LocalDateTime exitDate = position.getCloseDate() != null
                ? position.getCloseDate().atTime(LocalTime.NOON) : null;
        BigDecimal realizedForResponse = !position.isOpen() ? f.pnl() : null;
        return new PositionResponse(
                position.getId(),
                AssetType.VIOP.name(),
                position.getViopContract().getSymbol(),
                f.displayName(),
                null,
                f.qty(),
                position.getEntryDate() != null ? position.getEntryDate().atTime(LocalTime.NOON) : null,
                f.entryPrice(),
                exitDate,
                position.getClosePrice(),
                realizedForResponse,
                f.currentPriceTry(),
                f.entryNotional(),
                f.marketValue(),
                f.pnl(),
                f.pnlPercent(),
                null,
                meta);
    }

    private DerivativeFigures computeFigures(DerivativePosition position) {
        BigDecimal contractSize = position.getViopContract().getContractSize() != null
                ? position.getViopContract().getContractSize() : BigDecimal.ONE;
        BigDecimal qty = position.getQuantityLot() != null ? position.getQuantityLot() : BigDecimal.ZERO;
        BigDecimal entryPrice = position.getEntryPrice() != null ? position.getEntryPrice() : BigDecimal.ZERO;
        boolean closed = !position.isOpen();
        BigDecimal currentPriceTry = resolveCurrentPrice(position, closed);
        BigDecimal entryNotional = entryPrice.multiply(contractSize).multiply(qty)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnl = position.realizedOrUnrealizedPnl(currentPriceTry);
        if (pnl == null) pnl = BigDecimal.ZERO;
        pnl = pnl.setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        // Market value = current notional (current price × size × lots). PnL is reported separately and is
        // direction-aware (realizedOrUnrealizedPnl), so for a SHORT value − cost ≠ pnl — the row shows the
        // mark-to-market notional (falls as a short profits) and the signed PnL alongside it.
        BigDecimal marketValue = (currentPriceTry != null ? currentPriceTry : entryPrice)
                .multiply(contractSize).multiply(qty)
                .setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
        BigDecimal pnlPercent = entryNotional.signum() > 0
                ? pnl.multiply(HUNDRED).divide(entryNotional, MoneyScale.PRICE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String displayName = position.getDirection().name() + " · "
                + position.getViopContract().getSymbol() + (closed ? CLOSED_SUFFIX : "");
        return new DerivativeFigures(qty, entryPrice, currentPriceTry, entryNotional,
                marketValue, pnl, pnlPercent, closed, displayName);
    }

    private BigDecimal resolveCurrentPrice(DerivativePosition position, boolean closed) {
        if (closed) return position.getClosePrice();
        // lastPrice first, candle close as fallback. Same ordering as PortfolioSummaryService
        // openAndClosedDerivativeTotals and AllocationCalculator.addOpenDerivative — keeps the
        // positions row's MV aligned with the summary card and the allocation pie's VIOP slice.
        // Earlier "candle first, lastPrice fallback" picked the prior session's close even when a
        // fresh intraday tick had landed, drifting the row 0-2% from the card.
        BigDecimal contractLast = position.getViopContract().getLastPrice();
        BigDecimal liveSource = contractLast != null
                ? contractLast : pricingResolver.latestCandleClose(position.getViopContract().getSymbol());
        return pricingResolver.convertLiveToTry(liveSource, position.getViopContract().resolvePriceCurrency());
    }

    private DerivativeMeta buildMeta(DerivativePosition position, DerivativeFigures f) {
        String kindName = position.getViopContract().getKind() != null
                ? position.getViopContract().getKind().name() : null;
        BigDecimal maxLoss = null;
        BigDecimal maxGain = null;
        if (OPTION_KIND.equals(kindName)) {
            if (LONG_DIRECTION.equals(position.getDirection().name())) maxLoss = f.entryNotional();
            else maxGain = f.entryNotional();
        }
        return new DerivativeMeta(
                position.getDirection().name(),
                kindName,
                position.getViopContract().getContractSize(),
                position.lockedMargin(),
                position.getViopContract().getExpiryDate(),
                position.getViopContract().resolvePriceCurrency(),
                f.closed(),
                position.getViopContract().getStrikePrice(),
                maxLoss,
                maxGain,
                position.getViopContract().getDisplayName());
    }

    private record DerivativeFigures(BigDecimal qty, BigDecimal entryPrice, BigDecimal currentPriceTry,
                                      BigDecimal entryNotional, BigDecimal marketValue,
                                      BigDecimal pnl, BigDecimal pnlPercent,
                                      boolean closed, String displayName) {}
}
