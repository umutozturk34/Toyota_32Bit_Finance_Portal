package com.finance.app.service;

import com.finance.common.model.MarketType;

/**
 * Abstraction over the actual refresh work behind admin tasks (market type, bonds, news, macro),
 * decoupling {@link AdminTaskService} from the concrete cross-module wiring.
 */
public interface TaskRefreshRegistry {

    void runMarketRefresh(MarketType type);

    void runBondUpdate();

    void runNewsUpdate();

    void runMacroRefresh();
}
