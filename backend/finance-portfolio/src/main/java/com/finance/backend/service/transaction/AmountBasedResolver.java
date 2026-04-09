package com.finance.backend.service.transaction;

import com.finance.backend.exception.BadRequestException;
import com.finance.backend.model.AssetType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class AmountBasedResolver implements TransactionInputResolver {

    private static final BigDecimal MIN_AMOUNT_TRY = new BigDecimal("10");
    private static final int QTY_SCALE = 8;
    private static final int PRICE_SCALE = 4;

    @Override
    public boolean supports(AssetType assetType) {
        return assetType == AssetType.CRYPTO || assetType == AssetType.FOREX;
    }

    @Override
    public ResolvedInput resolve(BigDecimal quantity, BigDecimal amountTry, BigDecimal unitPrice) {
        if (amountTry != null && amountTry.compareTo(BigDecimal.ZERO) > 0) {
            return resolveByAmount(amountTry, unitPrice);
        }
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            return resolveByQuantity(quantity, unitPrice);
        }
        throw new BadRequestException("Tutar veya miktar belirtilmelidir");
    }

    private ResolvedInput resolveByAmount(BigDecimal amountTry, BigDecimal unitPrice) {
        if (amountTry.compareTo(MIN_AMOUNT_TRY) < 0) {
            throw new BadRequestException("Minimum işlem tutarı " + MIN_AMOUNT_TRY + " TRY");
        }
        BigDecimal qty = amountTry.divide(unitPrice, QTY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalCost = amountTry.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return new ResolvedInput(qty, totalCost);
    }

    private ResolvedInput resolveByQuantity(BigDecimal quantity, BigDecimal unitPrice) {
        BigDecimal qty = quantity.setScale(QTY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalCost = unitPrice.multiply(qty).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        if (totalCost.compareTo(MIN_AMOUNT_TRY) < 0) {
            throw new BadRequestException("Minimum işlem tutarı " + MIN_AMOUNT_TRY + " TRY");
        }
        return new ResolvedInput(qty, totalCost);
    }
}
