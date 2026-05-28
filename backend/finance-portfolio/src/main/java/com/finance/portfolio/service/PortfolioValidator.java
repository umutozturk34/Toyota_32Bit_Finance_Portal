package com.finance.portfolio.service;

import com.finance.common.exception.BusinessException;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
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
        BigDecimal price = request.entryPrice();
        if (price != null && limits.getMinPriceTry() != null && price.compareTo(limits.getMinPriceTry()) < 0) {
            throw new BusinessException("error.portfolio.lot.priceTooLow", limits.getMinPriceTry());
        }
        if (price != null && limits.getMaxPriceTry() != null && price.compareTo(limits.getMaxPriceTry()) > 0) {
            throw new BusinessException("error.portfolio.lot.priceTooHigh", limits.getMaxPriceTry());
        }
        BigDecimal qty = request.quantity();
        if (qty != null && limits.getMinQuantity() != null && qty.compareTo(limits.getMinQuantity()) < 0) {
            throw new BusinessException("error.portfolio.lot.quantityTooLow", limits.getMinQuantity());
        }
        if (qty != null && limits.getMaxQuantity() != null && qty.compareTo(limits.getMaxQuantity()) > 0) {
            throw new BusinessException("error.portfolio.lot.quantityTooHigh", limits.getMaxQuantity());
        }
    }

    /** Validates a sell: quantity not above the held amount, exit date not before entry and not in the future. */
    static void validateSell(PortfolioPosition position, PositionSellRequest request) {
        if (request.quantity().compareTo(position.getQuantity()) > 0) {
            throw new BusinessException("error.portfolio.sell.quantityExceedsPosition");
        }
        LocalDate exitDay = request.exitDate().toLocalDate();
        if (exitDay.isBefore(position.getEntryDate().toLocalDate())) {
            throw new BusinessException("error.portfolio.sell.dateBeforeEntry");
        }
        if (exitDay.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.sell.dateInFuture");
        }
    }
}
