package com.finance.market.core.service;

import com.finance.common.model.Currency;
import com.finance.common.model.MarketType;

import java.util.Set;

public interface NativeCurrencyStrategy {

    Set<MarketType> supports();

    Currency resolve(String code);
}
