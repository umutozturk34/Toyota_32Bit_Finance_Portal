package com.finance.portfolio.service;

import com.finance.common.exception.BusinessException;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.model.PortfolioPosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioValidatorTest {

    private LotLimits defaultLimits() {
        LotLimits limits = new LotLimits();
        limits.setMinEntryDate(LocalDate.of(1992, 1, 1));
        limits.setMinPriceTry(new BigDecimal("0.0001"));
        limits.setMaxPriceTry(new BigDecimal("1000000000"));
        limits.setMinQuantity(new BigDecimal("0.00000001"));
        limits.setMaxQuantity(new BigDecimal("1000000000"));
        return limits;
    }

    private PositionRequest request(LocalDateTime entryDate, BigDecimal price, BigDecimal qty) {
        return new PositionRequest("STOCK", "AKBNK", qty, entryDate, price, null, null);
    }

    @Test
    void shouldAcceptValidLot_whenAllFieldsWithinLimits() {
        PositionRequest req = request(LocalDateTime.now().minusDays(1),
                new BigDecimal("100"), new BigDecimal("10"));

        assertThatCode(() -> PortfolioValidator.validateLot(req, defaultLimits())).doesNotThrowAnyException();
    }

    @Test
    void shouldThrow_whenEntryDateBeforeMinEntryDate() {
        PositionRequest req = request(LocalDateTime.of(1980, 1, 1, 12, 0),
                new BigDecimal("100"), new BigDecimal("10"));

        assertThatThrownBy(() -> PortfolioValidator.validateLot(req, defaultLimits()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.entryDateTooOld");
    }

    @Test
    void shouldThrow_whenEntryDateIsInFuture() {
        PositionRequest req = request(LocalDateTime.now().plusDays(5),
                new BigDecimal("100"), new BigDecimal("10"));

        assertThatThrownBy(() -> PortfolioValidator.validateLot(req, defaultLimits()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.entryDateInFuture");
    }

    @Test
    void shouldThrow_whenPriceBelowMin() {
        PositionRequest req = request(LocalDateTime.now().minusDays(1),
                new BigDecimal("0.000001"), new BigDecimal("10"));

        assertThatThrownBy(() -> PortfolioValidator.validateLot(req, defaultLimits()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.priceTooLow");
    }

    @Test
    void shouldThrow_whenPriceAboveMax() {
        PositionRequest req = request(LocalDateTime.now().minusDays(1),
                new BigDecimal("2000000000"), new BigDecimal("10"));

        assertThatThrownBy(() -> PortfolioValidator.validateLot(req, defaultLimits()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.priceTooHigh");
    }

    @Test
    void shouldThrow_whenQuantityBelowMin() {
        PositionRequest req = request(LocalDateTime.now().minusDays(1),
                new BigDecimal("100"), new BigDecimal("0.0000000001"));

        assertThatThrownBy(() -> PortfolioValidator.validateLot(req, defaultLimits()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.quantityTooLow");
    }

    @Test
    void shouldThrow_whenQuantityAboveMax() {
        PositionRequest req = request(LocalDateTime.now().minusDays(1),
                new BigDecimal("100"), new BigDecimal("2000000000"));

        assertThatThrownBy(() -> PortfolioValidator.validateLot(req, defaultLimits()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.quantityTooHigh");
    }

    @Test
    void shouldAcceptLot_whenLimitsHaveNullBounds() {
        LotLimits limits = new LotLimits();
        limits.setMinEntryDate(null);
        limits.setMinPriceTry(null);
        limits.setMaxPriceTry(null);
        limits.setMinQuantity(null);
        limits.setMaxQuantity(null);
        PositionRequest req = request(LocalDateTime.now().minusDays(1),
                new BigDecimal("100"), new BigDecimal("10"));

        assertThatCode(() -> PortfolioValidator.validateLot(req, limits)).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptLot_whenRequestFieldsAreNull() {
        PositionRequest req = new PositionRequest("STOCK", "AKBNK", null, null, null, null, null);

        assertThatCode(() -> PortfolioValidator.validateLot(req, defaultLimits())).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptSell_whenQuantityAndDatesAreValid() {
        PortfolioPosition pos = PortfolioPosition.builder()
                .quantity(new BigDecimal("10"))
                .entryDate(LocalDateTime.now().minusDays(10))
                .build();
        PositionSellRequest req = new PositionSellRequest(new BigDecimal("5"),
                new BigDecimal("100"), LocalDateTime.now().minusDays(1));

        assertThatCode(() -> PortfolioValidator.validateSell(pos, req)).doesNotThrowAnyException();
    }

    @Test
    void shouldThrow_whenSellQuantityExceedsPosition() {
        PortfolioPosition pos = PortfolioPosition.builder()
                .quantity(new BigDecimal("5"))
                .entryDate(LocalDateTime.now().minusDays(10))
                .build();
        PositionSellRequest req = new PositionSellRequest(new BigDecimal("10"),
                new BigDecimal("100"), LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> PortfolioValidator.validateSell(pos, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.sell.quantityExceedsPosition");
    }

    @Test
    void shouldThrow_whenSellDateBeforeEntry() {
        PortfolioPosition pos = PortfolioPosition.builder()
                .quantity(new BigDecimal("10"))
                .entryDate(LocalDateTime.now().minusDays(1))
                .build();
        PositionSellRequest req = new PositionSellRequest(new BigDecimal("5"),
                new BigDecimal("100"), LocalDateTime.now().minusDays(5));

        assertThatThrownBy(() -> PortfolioValidator.validateSell(pos, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.sell.dateBeforeEntry");
    }

    @Test
    void shouldThrow_whenSellDateInFuture() {
        PortfolioPosition pos = PortfolioPosition.builder()
                .quantity(new BigDecimal("10"))
                .entryDate(LocalDateTime.now().minusDays(10))
                .build();
        PositionSellRequest req = new PositionSellRequest(new BigDecimal("5"),
                new BigDecimal("100"), LocalDateTime.now().plusDays(5));

        assertThatThrownBy(() -> PortfolioValidator.validateSell(pos, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.sell.dateInFuture");
    }
}
