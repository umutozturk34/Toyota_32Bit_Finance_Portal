package com.finance.market.macro.service;

import com.finance.market.macro.model.MacroCategory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/** Macro provider bound to the DEPOSIT category. */
@Service
public class MacroDepositAssetProvider extends MacroMarketAssetProvider {

    public MacroDepositAssetProvider(MacroIndicatorQueryService queryService, MessageSource messageSource) {
        super(queryService, MacroCategory.DEPOSIT, messageSource);
    }
}
