package com.finance.portfolio.service;

import com.finance.common.exception.BusinessException;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.model.PortfolioPosition;

import java.math.BigDecimal;
import java.time.LocalDate;

final class PortfolioValidator {

    private PortfolioValidator() {
    }

    static void validateLot(PositionRequest request, LotLimits limits) {
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
