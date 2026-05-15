package com.finance.market.viop.dto;

import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopOptionSide;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ViopContractSpec(
        String symbol,
        String displayName,
        ViopContractKind kind,
        String underlying,
        LocalDate expiryDate,
        BigDecimal contractSize,
        BigDecimal initialMargin,
        String settlementType,
        String currency,
        ViopOptionSide optionSide,
        BigDecimal strikePrice,
        String exerciseStyle
) {

    public static ViopContractSpec future(String symbol, String displayName, String underlying,
                                          LocalDate expiry, BigDecimal contractSize,
                                          BigDecimal initialMargin, String settlementType, String currency) {
        return new ViopContractSpec(symbol, displayName, ViopContractKind.FUTURE, underlying, expiry,
                contractSize, initialMargin, settlementType, currency, null, null, null);
    }

    public static ViopContractSpec option(String symbol, String displayName, String underlying,
                                          LocalDate expiry, BigDecimal contractSize,
                                          BigDecimal initialMargin, String settlementType, String currency,
                                          ViopOptionSide side, BigDecimal strike, String exerciseStyle) {
        return new ViopContractSpec(symbol, displayName, ViopContractKind.OPTION, underlying, expiry,
                contractSize, initialMargin, settlementType, currency, side, strike, exerciseStyle);
    }
}
