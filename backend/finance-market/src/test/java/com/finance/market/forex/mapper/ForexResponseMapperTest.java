package com.finance.market.forex.mapper;

import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.forex.dto.response.ForexCandleResponse;
import com.finance.market.forex.model.Forex;
import com.finance.market.forex.model.ForexCandle;
import com.finance.shared.dto.response.ForexMetadata;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForexResponseMapperTest {

    private final ForexResponseMapper mapper = new ForexResponseMapperImpl();

    @Test
    void should_buildMetadata_when_forexFullyPopulated() {
        Forex usd = Forex.builder().currencyCode("USD")
                .buyingPrice(new BigDecimal("45.19"))
                .sellingPrice(new BigDecimal("45.27"))
                .effectiveBuyingPrice(new BigDecimal("45.15"))
                .effectiveSellingPrice(new BigDecimal("45.33"))
                .build();

        ForexMetadata metadata = mapper.buildMetadata(usd);

        assertThat(metadata.buyingPrice()).isEqualByComparingTo("45.19");
        assertThat(metadata.sellingPrice()).isEqualByComparingTo("45.27");
        assertThat(metadata.effectiveBuyingPrice()).isEqualByComparingTo("45.15");
        assertThat(metadata.effectiveSellingPrice()).isEqualByComparingTo("45.33");
        assertThat(metadata.tradable()).isTrue();
    }

    @Test
    void should_returnFalseTradable_when_buyingMissing() {
        Forex xdr = Forex.builder().currencyCode("XDR")
                .buyingPrice(new BigDecimal("62.20"))
                .build();

        ForexMetadata metadata = mapper.buildMetadata(xdr);

        assertThat(metadata.tradable()).isFalse();
        assertThat(metadata.effectiveBuyingPrice()).isNull();
        assertThat(metadata.effectiveSellingPrice()).isNull();
    }

    @Test
    void should_mapForexToMarketAssetResponse_when_fullyPopulated() {
        Forex usd = Forex.builder().currencyCode("USD")
                .buyingPrice(new BigDecimal("45.19"))
                .sellingPrice(new BigDecimal("45.27"))
                .effectiveBuyingPrice(new BigDecimal("45.15"))
                .effectiveSellingPrice(new BigDecimal("45.33"))
                .build();
        usd.setName("ABD Doları");
        usd.setImage("🇺🇸");
        usd.setChangeAmount(new BigDecimal("0.08"));
        usd.setChangePercent(new BigDecimal("0.17"));

        MarketAssetResponse response = mapper.toMarketAssetResponse(usd);

        assertThat(response.code()).isEqualTo("USD");
        assertThat(response.name()).isEqualTo("ABD Doları");
        assertThat(response.price()).isEqualByComparingTo("45.27");
        assertThat(response.changeAmount()).isEqualByComparingTo("0.08");
        assertThat(response.changePercent()).isEqualByComparingTo("0.17");
        assertThat(response.image()).isEqualTo("🇺🇸");
        assertThat(response.metadata()).isInstanceOf(ForexMetadata.class);
    }

    @Test
    void should_mapListOfForex_when_toMarketAssetResponses() {
        Forex usd = Forex.builder().currencyCode("USD")
                .buyingPrice(new BigDecimal("45.19")).sellingPrice(new BigDecimal("45.27")).build();
        Forex eur = Forex.builder().currencyCode("EUR")
                .buyingPrice(new BigDecimal("53.13")).sellingPrice(new BigDecimal("53.23")).build();

        List<MarketAssetResponse> response = mapper.toMarketAssetResponses(List.of(usd, eur));

        assertThat(response).extracting(MarketAssetResponse::code).containsExactly("USD", "EUR");
    }

    @Test
    void should_mapCandlesToResponses_when_toForexCandleResponses() {
        ForexCandle candle = ForexCandle.builder().currencyCode("USD")
                .candleDate(LocalDateTime.of(2026, 5, 11, 0, 0))
                .sellingPrice(new BigDecimal("45.27"))
                .buyingPrice(new BigDecimal("45.19"))
                .effectiveBuyingPrice(new BigDecimal("45.15"))
                .effectiveSellingPrice(new BigDecimal("45.33"))
                .build();

        List<ForexCandleResponse> response = mapper.toForexCandleResponses(List.of(candle));

        assertThat(response).hasSize(1);
        ForexCandleResponse first = response.getFirst();
        assertThat(first.sellingPrice()).isEqualByComparingTo("45.27");
        assertThat(first.buyingPrice()).isEqualByComparingTo("45.19");
        assertThat(first.effectiveBuyingPrice()).isEqualByComparingTo("45.15");
        assertThat(first.effectiveSellingPrice()).isEqualByComparingTo("45.33");
        assertThat(first.close()).isEqualByComparingTo("45.27");
        assertThat(first.open()).isEqualByComparingTo("45.27");
    }
}
