package com.finance.market.macro.service;

import com.finance.market.macro.model.MacroCategory;
import org.springframework.stereotype.Service;

@Service
public class MacroInflationAssetProvider extends MacroMarketAssetProvider {

    public MacroInflationAssetProvider(MacroIndicatorQueryService queryService) {
        super(queryService, MacroCategory.INFLATION);
    }
}
