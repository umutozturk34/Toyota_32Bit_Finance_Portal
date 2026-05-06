package com.finance.notification.market;

/**
 * Markets the notification microservice tracks open/closed sessions for.
 *
 * <p>Distinct from {@code com.finance.common.model.MarketType} on purpose:
 * the monolith's market enum carries pricing/redis-label fields that are
 * irrelevant to session resolution, and CRYPTO has no session at all.
 * Adding a new tradable market means adding a constant here, a schedule
 * entry under {@code notification.market-hours.markets} in YAML, and a
 * mapping on the frontend badge — three open-closed extension points
 * with no need to touch the resolver, controller, scheduler or handler.
 */
public enum SessionMarket {
    STOCK("Hisse"),
    FOREX("Döviz"),
    FUND("Fon"),
    COMMODITY("Emtia"),
    BOND("Tahvil"),
    NEWS("Haberler"),
    CRYPTO("Kripto");

    private final String displayLabel;

    SessionMarket(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String displayLabel() {
        return displayLabel;
    }
}
