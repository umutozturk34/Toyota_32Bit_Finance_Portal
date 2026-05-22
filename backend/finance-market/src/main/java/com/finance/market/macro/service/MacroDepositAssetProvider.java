package com.finance.market.macro.service;

import com.finance.market.macro.model.MacroCategory;
import org.springframework.stereotype.Service;

@Service
public class MacroDepositAssetProvider extends MacroMarketAssetProvider {

    public MacroDepositAssetProvider(MacroIndicatorQueryService queryService) {
        super(queryService, MacroCategory.DEPOSIT);
    }
}
