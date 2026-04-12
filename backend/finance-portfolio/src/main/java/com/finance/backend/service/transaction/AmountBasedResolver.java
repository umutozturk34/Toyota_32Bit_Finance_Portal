package com.finance.backend.service.transaction;

import com.finance.backend.config.AppProperties;
import com.finance.backend.exception.BadRequestException;
import com.finance.backend.model.AssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Log4j2
@Component
@RequiredArgsConstructor
public class AmountBasedResolver implements TransactionInputResolver {

    private static final int QTY_SCALE = 8;
    private static final int PRICE_SCALE = 4;
    private final AppProperties appProperties;

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
        BigDecimal minAmountTry = appProperties.getPortfolio().getMinTransactionAmountTry();
        String currency = appProperties.getPortfolio().getDefaultCurrency();
        if (amountTry.compareTo(minAmountTry) < 0) {
            throw new BadRequestException("Minimum işlem tutarı " + minAmountTry + " " + currency);
        }
        BigDecimal qty = amountTry.divide(unitPrice, QTY_SCALE, RoundingMode.DOWN);
        BigDecimal totalCost = unitPrice.multiply(qty).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return new ResolvedInput(qty, totalCost);
    }

    private ResolvedInput resolveByQuantity(BigDecimal quantity, BigDecimal unitPrice) {
        BigDecimal minAmountTry = appProperties.getPortfolio().getMinTransactionAmountTry();
        String currency = appProperties.getPortfolio().getDefaultCurrency();
        BigDecimal qty = quantity.setScale(QTY_SCALE, RoundingMode.DOWN);
        BigDecimal totalCost = unitPrice.multiply(qty).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        if (totalCost.compareTo(minAmountTry) < 0) {
            throw new BadRequestException("Minimum işlem tutarı " + minAmountTry + " " + currency);
        }
        return new ResolvedInput(qty, totalCost);
    }
}
