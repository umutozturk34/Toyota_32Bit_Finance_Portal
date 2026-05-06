package com.finance.fund.mapper;
import com.finance.common.model.MarketType;

import com.finance.common.mapper.MarketMetadataBuilder;


import com.finance.fund.dto.response.FundCandleResponse;
import com.finance.common.dto.response.FundMetadata;
import com.finance.common.dto.response.MarketAssetResponse;
import com.finance.fund.model.Fund;
import com.finance.fund.model.FundCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class FundResponseMapper implements MarketMetadataBuilder<Fund, FundMetadata> {

    public abstract List<FundCandleResponse> toFundCandleResponses(List<FundCandle> candles);

    @Mapping(target = "code", source = "fundCode")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "changeAmount", ignore = true)
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.FUND)")
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "metadata", source = "fund", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Fund fund);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Fund> funds);

    @Override
    @Named("metadata")
    public FundMetadata buildMetadata(Fund fund) {
        return new FundMetadata(
                fund.getFundType() == null ? null : fund.getFundType().name(),
                fund.getPortfolioSize(),
                fund.getInvestorCount(),
                fund.getBulletinPrice(),
                fund.getShareCount()
        );
    }
}
