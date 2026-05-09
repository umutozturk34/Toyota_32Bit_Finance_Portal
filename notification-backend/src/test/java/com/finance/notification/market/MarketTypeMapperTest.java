package com.finance.notification.market;

import com.finance.notification.market.session.SessionMarket;

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
    void should_mapBond_when_marketTypeIsBond() {
        assertThat(MarketTypeMapper.fromMarketType(MarketType.BOND)).contains(SessionMarket.BOND);
    }

    @Test
    void should_mapCrypto_when_marketTypeIsCrypto() {
        Optional<SessionMarket> mapped = MarketTypeMapper.fromMarketType(MarketType.CRYPTO);

        assertThat(mapped).contains(SessionMarket.CRYPTO);
    }
}
