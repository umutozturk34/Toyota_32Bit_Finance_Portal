package com.finance.notification.market;

import com.finance.notification.market.session.*;

import com.finance.common.model.MarketType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTypeMapperTest {

    @Test
    void should_mapStockToStock_when_marketTypeIsStock() {
        Optional<SessionMarket> mapped = MarketTypeMapper.fromMarketType(MarketType.STOCK);

        assertThat(mapped).contains(SessionMarket.STOCK);
    }

    @Test
    void should_mapBondAndNews_when_recentlyAddedTypesPassed() {
        assertThat(MarketTypeMapper.fromMarketType(MarketType.BOND)).contains(SessionMarket.BOND);
        assertThat(MarketTypeMapper.fromMarketType(MarketType.NEWS)).contains(SessionMarket.NEWS);
    }

    @Test
    void should_mapCrypto_when_marketTypeIsCrypto() {
        Optional<SessionMarket> mapped = MarketTypeMapper.fromMarketType(MarketType.CRYPTO);

        assertThat(mapped).contains(SessionMarket.CRYPTO);
    }
}
