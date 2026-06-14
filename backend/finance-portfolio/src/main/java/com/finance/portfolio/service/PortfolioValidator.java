package com.finance.portfolio.service;

import com.finance.common.exception.BusinessException;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.PortfolioPosition;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Stateless guards for position commands: enforces configured lot limits and sell constraints, throwing localized business errors on violation. */
@Log4j2
final class PortfolioValidator {

    private PortfolioValidator() {
    }

    /** Validates a new/updated lot against entry-date, price and quantity bounds (entry must not be in the future). */
    static void validateLot(PositionRequest request, LotLimits limits) {
        log.debug("Validating lot: type={} code={} qty={} price={} entryDate={}",
                request.assetType(), request.assetCode(), request.quantity(),
                request.entryPrice(), request.entryDate());
        LocalDate entryDay = request.entryDate() != null ? request.entryDate().toLocalDate() : null;
        if (entryDay != null && limits.getMinEntryDate() != null && entryDay.isBefore(limits.getMinEntryDate())) {
            throw new BusinessException("error.portfolio.lot.entryDateTooOld", limits.getMinEntryDate());
        }
        if (entryDay != null && entryDay.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.lot.entryDateInFuture");
        }
        // Price bounds are TRY (minPriceTry/maxPriceTry); the raw request price is still in its native
        // currency here, so validate the converted price via validatePriceTry AFTER toTryOnDate — checking
        // the raw native price against TRY bounds would mis-judge USD/EUR-priced lots.
        BigDecimal qty = request.quantity();
        if (qty != null && limits.getMinQuantity() != null && qty.compareTo(limits.getMinQuantity()) < 0) {
            throw new BusinessException("error.portfolio.lot.quantityTooLow", limits.getMinQuantity());
        }
        if (qty != null && limits.getMaxQuantity() != null && qty.compareTo(limits.getMaxQuantity()) > 0) {
            throw new BusinessException("error.portfolio.lot.quantityTooHigh", limits.getMaxQuantity());
        }
    }

    /**
     * Enforces whole-unit quantities for share-based classes (stocks, funds): a fractional quantity such
     * as 12.5 shares is rejected. Crypto/forex/commodity are fractional and pass through; a null type
     * (unresolved tracked asset) is skipped. Call AFTER the asset type is known.
     */
    static void validateWholeUnit(AssetType assetType, BigDecimal quantity) {
        if (assetType != null && assetType.isWholeUnitOnly() && !isWholeNumber(quantity)) {
            throw new BusinessException("error.portfolio.lot.wholeUnitRequired");
        }
    }

    /** True when the value has no fractional part (e.g. 10, 10.0, 600 are whole; 12.5, 0.0001 are not). */
    private static boolean isWholeNumber(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= 0;
    }

    /** Validates a TRY-converted lot price against the configured TRY bounds (call AFTER currency conversion). */
    static void validatePriceTry(BigDecimal priceTry, LotLimits limits) {
        if (priceTry == null) return;
        if (limits.getMinPriceTry() != null && priceTry.compareTo(limits.getMinPriceTry()) < 0) {
            throw new BusinessException("error.portfolio.lot.priceTooLow", limits.getMinPriceTry());
        }
        if (limits.getMaxPriceTry() != null && priceTry.compareTo(limits.getMaxPriceTry()) > 0) {
            throw new BusinessException("error.portfolio.lot.priceTooHigh", limits.getMaxPriceTry());
        }
    }

    /**
     * Rejects a lot whose TRY value (price × quantity) would overflow the numeric(23,8) snapshot money
     * columns. The per-field price/quantity caps don't bound their product, so an absurd lot otherwise
     * passes validation and then breaks the whole portfolio chart at snapshot time. Call AFTER currency
     * conversion (priceTry).
     */
    static void validateLotValueTry(BigDecimal priceTry, BigDecimal quantity, LotLimits limits) {
        if (priceTry == null || quantity == null || limits.getMaxLotValueTry() == null) return;
        if (priceTry.multiply(quantity).compareTo(limits.getMaxLotValueTry()) > 0) {
            throw new BusinessException("error.portfolio.lot.valueTooHigh", limits.getMaxLotValueTry());
        }
    }

    /** Validates an exit/close date: not before entry and not in the future. */
    static void validateExit(LocalDate entryDate, LocalDate exitDate) {
        if (exitDate == null) return;
        if (entryDate != null && exitDate.isBefore(entryDate)) {
            throw new BusinessException("error.portfolio.sell.dateBeforeEntry");
        }
        if (exitDate.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.sell.dateInFuture");
        }
    }

    /** Validates a sell: quantity not above the held amount, exit date not before entry and not in the future. */
    static void validateSell(PortfolioPosition position, PositionSellRequest request) {
        BigDecimal sellQty = request.quantity();
        if (sellQty.compareTo(position.getQuantity()) > 0) {
            throw new BusinessException("error.portfolio.sell.quantityExceedsPosition");
        }
        // For share-based assets a partial sell must leave both a whole sold amount AND a whole remaining
        // amount, so a lot can never be split into a fractional piece (e.g. 12.5 of 25, or 10 of a legacy
        // 10.5 leaving 0.5). A full close is always allowed — it lets a legacy fractional lot be cleared
        // out entirely rather than trapped.
        AssetType type = position.getAssetType();
        boolean fullClose = sellQty.compareTo(position.getQuantity()) == 0;
        boolean leavesWhole = isWholeNumber(sellQty) && isWholeNumber(position.getQuantity().subtract(sellQty));
        if (type != null && type.isWholeUnitOnly() && !fullClose && !leavesWhole) {
            throw new BusinessException("error.portfolio.sell.wholeUnitRequired");
        }
        validateExit(position.getEntryDate().toLocalDate(), request.exitDate().toLocalDate());
    }
}
