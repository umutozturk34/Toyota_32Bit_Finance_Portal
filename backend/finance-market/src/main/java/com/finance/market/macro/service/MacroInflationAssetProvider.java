package com.finance.market.macro.service;

import com.finance.market.macro.model.MacroCategory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
public class MacroInflationAssetProvider extends MacroMarketAssetProvider {

    public MacroInflationAssetProvider(MacroIndicatorQueryService queryService, MessageSource messageSource) {
        super(queryService, MacroCategory.INFLATION, messageSource);
    }
}
