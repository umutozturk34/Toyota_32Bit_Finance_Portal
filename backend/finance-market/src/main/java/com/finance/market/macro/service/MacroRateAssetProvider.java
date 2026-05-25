package com.finance.market.macro.service;

import com.finance.market.macro.model.MacroCategory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class MacroRateAssetProvider extends MacroMarketAssetProvider {

    public MacroRateAssetProvider(MacroIndicatorQueryService queryService, MessageSource messageSource) {
        super(queryService, MacroCategory.RATES, messageSource);
    }
}
