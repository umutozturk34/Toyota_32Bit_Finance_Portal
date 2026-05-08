package com.finance.notification.market;

import com.finance.notification.market.session.*;

import com.finance.common.model.MarketType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public final class MarketTypeMapper {

    private static final Map<MarketType, SessionMarket> MAPPING = buildMapping();

    private MarketTypeMapper() {
    }

    private static Map<MarketType, SessionMarket> buildMapping() {
        Map<MarketType, SessionMarket> map = new EnumMap<>(MarketType.class);
        map.put(MarketType.STOCK, SessionMarket.STOCK);
        map.put(MarketType.FOREX, SessionMarket.FOREX);
        map.put(MarketType.FUND, SessionMarket.FUND);
        map.put(MarketType.COMMODITY, SessionMarket.COMMODITY);
        map.put(MarketType.BOND, SessionMarket.BOND);
        map.put(MarketType.NEWS, SessionMarket.NEWS);
        map.put(MarketType.CRYPTO, SessionMarket.CRYPTO);
        return map;
    }

    public static Optional<SessionMarket> fromMarketType(MarketType marketType) {
        return Optional.ofNullable(MAPPING.get(marketType));
    }
}
