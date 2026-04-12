package com.finance.backend.service.transaction;

import com.finance.backend.exception.BadRequestException;
import com.finance.backend.model.AssetType;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Log4j2
@Component
public class QuantityBasedResolver implements TransactionInputResolver {

    private static final int QTY_SCALE = 8;
    private static final int PRICE_SCALE = 4;

    @Override
    public boolean supports(AssetType assetType) {
        return assetType == AssetType.STOCK || assetType == AssetType.FUND;
    }

    @Override
    public ResolvedInput resolve(BigDecimal quantity, BigDecimal amountTry, BigDecimal unitPrice) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Miktar belirtilmelidir");
        }
        if (quantity.stripTrailingZeros().scale() > 0) {
            throw new BadRequestException("Hisse ve fon işlemlerinde sadece tam adet girilebilir");
        }
        if (quantity.compareTo(BigDecimal.ONE) < 0) {
            throw new BadRequestException("Minimum 1 adet girilmelidir");
        }
        BigDecimal qty = quantity.setScale(QTY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalCost = unitPrice.multiply(qty).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return new ResolvedInput(qty, totalCost);
    }
}
