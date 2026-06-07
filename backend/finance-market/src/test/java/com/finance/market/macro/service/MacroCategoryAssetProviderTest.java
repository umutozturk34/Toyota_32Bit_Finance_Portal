package com.finance.market.macro.service;

import com.finance.common.model.MarketType;
import com.finance.market.core.service.MarketAssetProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MacroCategoryAssetProviderTest {

    @Mock private MacroIndicatorQueryService queryService;
    @Mock private MessageSource messageSource;

    @Test
    void should_reportRateMarketType_when_rateProviderQueried() {
        MarketAssetProvider provider = new MacroRateAssetProvider(queryService, messageSource);

        assertThat(provider.getType()).isEqualTo(MarketType.MACRO_RATE);
    }

    @Test
    void should_reportInflationMarketType_when_inflationProviderQueried() {
        MarketAssetProvider provider = new MacroInflationAssetProvider(queryService, messageSource);

        assertThat(provider.getType()).isEqualTo(MarketType.MACRO_INFLATION);
    }

    @Test
    void should_reportDepositMarketType_when_depositProviderQueried() {
        MarketAssetProvider provider = new MacroDepositAssetProvider(queryService, messageSource);

        assertThat(provider.getType()).isEqualTo(MarketType.MACRO_DEPOSIT);
    }
}
