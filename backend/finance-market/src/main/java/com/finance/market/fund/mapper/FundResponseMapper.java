package com.finance.market.fund.mapper;

import com.finance.market.core.mapper.MarketMetadataBuilder;


import com.finance.market.fund.dto.response.FundCandleResponse;
import com.finance.market.fund.repository.FundAllocationRepository;
import com.finance.shared.dto.response.FundMetadata;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.market.fund.model.Fund;
import com.finance.market.fund.model.FundCandle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class FundResponseMapper implements MarketMetadataBuilder<Fund, FundMetadata> {

    @Autowired
    protected FundAllocationRepository allocationRepository;

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
        List<FundMetadata.AllocationEntry> allocations = allocationRepository
                .findByFundCodeOrderByPercentageDesc(fund.getFundCode()).stream()
                .map(a -> new FundMetadata.AllocationEntry(a.getAssetClass(), a.getPercentage()))
                .toList();
        return new FundMetadata(
                fund.getFundType() == null ? null : fund.getFundType().name(),
                fund.getPortfolioSize(),
                fund.getInvestorCount(),
                fund.getBulletinPrice(),
                fund.getShareCount(),
                fund.getRiskValue(),
                fund.getCategory(),
                fund.getSubCategory(),
                fund.getCategoryRank(),
                fund.getCategoryTotalFunds(),
                fund.getMarketShare(),
                fund.getReturn1m(),
                fund.getReturn3m(),
                fund.getReturn6m(),
                fund.getReturn1y(),
                fund.getReturnYtd(),
                fund.getReturn3y(),
                fund.getReturn5y(),
                allocations
        );
    }
}
