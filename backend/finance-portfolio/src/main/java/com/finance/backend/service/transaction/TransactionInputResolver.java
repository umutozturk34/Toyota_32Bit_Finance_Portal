package com.finance.backend.service.transaction;

import com.finance.backend.model.AssetType;

import java.math.BigDecimal;

public interface TransactionInputResolver {

    boolean supports(AssetType assetType);

    ResolvedInput resolve(BigDecimal quantity, BigDecimal amountTry, BigDecimal unitPrice);
}
