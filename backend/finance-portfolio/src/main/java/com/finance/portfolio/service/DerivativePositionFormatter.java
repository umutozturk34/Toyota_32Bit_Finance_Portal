package com.finance.portfolio.service;

import com.finance.common.model.MarketType;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.dto.response.DerivativeMeta;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.MoneyScale;
import com.finance.shared.service.AssetPricingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Log4j2
@Component
@RequiredArgsConstructor
class DerivativePositionFormatter {

    private static final String OPTION_KIND = "OPTION";
    private static final String LONG_DIRECTION = "LONG";
    private static final String CLOSED_SUFFIX = " · KAPALI";
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ViopCandleRepository viopCandleRepository;
    private final AssetPricingPort pricingPort;

    PositionResponse toPositionResponse(DerivativePosition position) {
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
        BigDecimal marketValue = entryNotional.add(pnl).setScale(MoneyScale.PRICE, RoundingMode.HALF_UP);
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
        BigDecimal latestClose = latestCandleClose(position.getViopContract().getSymbol());
        BigDecimal liveSource = latestClose != null ? latestClose : position.getViopContract().getLastPrice();
        return convertLiveToTry(liveSource, position.getViopContract().getCurrency());
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
                position.getViopContract().getCurrency(),
                f.closed(),
                position.getViopContract().getStrikePrice(),
                maxLoss,
                maxGain,
                position.getViopContract().getDisplayName());
    }

    private BigDecimal latestCandleClose(String symbol) {
        return viopCandleRepository.findFirstBySymbolOrderByCandleDateDesc(symbol)
                .map(ViopCandle::getClose)
                .orElse(null);
    }

    private BigDecimal convertLiveToTry(BigDecimal nativePrice, String currency) {
        if (nativePrice == null) return null;
        if (currency == null || currency.isBlank() || "TRY".equalsIgnoreCase(currency)) return nativePrice;
        BigDecimal rate = pricingPort.getExitPriceTry(MarketType.FOREX, currency.toUpperCase());
        return rate != null && rate.signum() > 0 ? nativePrice.multiply(rate) : nativePrice;
    }

    private record DerivativeFigures(BigDecimal qty, BigDecimal entryPrice, BigDecimal currentPriceTry,
                                      BigDecimal entryNotional, BigDecimal marketValue,
                                      BigDecimal pnl, BigDecimal pnlPercent,
                                      boolean closed, String displayName) {}
}
