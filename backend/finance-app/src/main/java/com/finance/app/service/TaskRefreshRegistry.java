package com.finance.app.service;

import com.finance.common.model.MarketType;

public interface TaskRefreshRegistry {

    void runMarketRefresh(MarketType type);

    void runBondUpdate();

    void runNewsUpdate();

    void runMacroRefresh();
}
