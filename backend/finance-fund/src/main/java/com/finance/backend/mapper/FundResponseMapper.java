package com.finance.backend.mapper;

import com.finance.backend.dto.response.FundCandleResponse;
import com.finance.backend.dto.response.MarketAssetResponse;
import com.finance.backend.model.Fund;
import com.finance.backend.model.FundCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class FundResponseMapper {

    public abstract List<FundCandleResponse> toFundCandleResponses(List<FundCandle> candles);

    @Mapping(target = "code", source = "fundCode")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "changeAmount", ignore = true)
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.FUND)")
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "metadata", source = "fund", qualifiedByName = "fundMetadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Fund fund);

    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Fund> funds);

    @Named("fundMetadata")
    protected Map<String, Object> buildFundMetadata(Fund fund) {
        Map<String, Object> metadata = new HashMap<>();
        if (fund.getFundType() != null) {
            metadata.put("fundType", fund.getFundType());
        }
        if (fund.getPortfolioSize() != null) {
            metadata.put("portfolioSize", fund.getPortfolioSize());
        }
        if (fund.getInvestorCount() != null) {
            metadata.put("investorCount", fund.getInvestorCount());
        }
        if (fund.getBulletinPrice() != null) {
            metadata.put("bulletinPrice", fund.getBulletinPrice());
        }
        if (fund.getShareCount() != null) {
            metadata.put("shareCount", fund.getShareCount());
        }
        return metadata;
    }
}
