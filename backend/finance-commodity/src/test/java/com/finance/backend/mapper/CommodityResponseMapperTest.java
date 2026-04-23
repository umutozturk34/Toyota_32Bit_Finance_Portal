package com.finance.backend.mapper;

import com.finance.backend.dto.response.CommodityMetadata;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.Commodity;
import com.finance.backend.model.MarketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommodityResponseMapperTest {

    private CommodityResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(CommodityResponseMapper.class);
    }

    @Test
    void toMarketAssetResponseMapsCoreFields() {
        Commodity commodity = buildCommodity("GC=F");

        MarketAssetResponse response = mapper.toMarketAssetResponse(commodity);

        assertThat(response.code()).isEqualTo("GC=F");
        assertThat(response.price()).isEqualByComparingTo("160000.5000");
        assertThat(response.changeAmount()).isEqualByComparingTo("1600.0000");
        assertThat(response.changePercent()).isEqualByComparingTo("1.0000");
        assertThat(response.type()).isEqualTo(MarketType.COMMODITY);
        assertThat(response.lastUpdated()).isNotNull();
    }

    @Test
    void toMarketAssetResponseBuildsCommodityMetadata() {
        Commodity commodity = buildCommodity("GC=F");

        MarketAssetResponse response = mapper.toMarketAssetResponse(commodity);

        assertThat(response.metadata()).isInstanceOf(CommodityMetadata.class);
        CommodityMetadata metadata = (CommodityMetadata) response.metadata();
        assertThat(metadata.currentPriceUsd()).isEqualByComparingTo("4000.0000");
        assertThat(metadata.previousPriceUsd()).isEqualByComparingTo("3960.0000");
        assertThat(metadata.unit()).isEqualTo("oz");
    }

    @Test
    void toMarketAssetResponsesPreservesListSize() {
        List<Commodity> commodities = List.of(buildCommodity("GC=F"), buildCommodity("SI=F"));

        List<MarketAssetResponse> responses = mapper.toMarketAssetResponses(commodities);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(MarketAssetResponse::code).containsExactly("GC=F", "SI=F");
    }

    @Test
    void toMarketAssetResponseReturnsNullForNullInput() {
        assertThat(mapper.toMarketAssetResponse(null)).isNull();
    }

    private Commodity buildCommodity(String code) {
        Commodity commodity = new Commodity();
        commodity.setCommodityCode(code);
        commodity.setCurrentPrice(new BigDecimal("160000.5000"));
        commodity.setCurrentPriceUsd(new BigDecimal("4000.0000"));
        commodity.setPreviousPriceUsd(new BigDecimal("3960.0000"));
        commodity.setChangeAmount(new BigDecimal("1600.0000"));
        commodity.setChangePercent(new BigDecimal("1.0000"));
        commodity.setUnit("oz");
        commodity.setYahooUpdatedAt(LocalDateTime.of(2026, 4, 21, 9, 0));
        return commodity;
    }
}
