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

/**
 * MapStruct mapper that exposes fund entities through the unified market API surface. It maps
 * {@link Fund} to {@link MarketAssetResponse} and {@link FundCandle} history to
 * {@link FundCandleResponse}, and supplies the fund-specific {@link FundMetadata} via the
 * {@link MarketMetadataBuilder} contract. Building metadata also reads the fund's asset-class
 * allocation breakdown from {@code allocationRepository}.
 */
@Mapper(componentModel = "spring")
public abstract class FundResponseMapper implements MarketMetadataBuilder<Fund, FundMetadata> {

    private FundAllocationRepository allocationRepository;

    @Autowired
    protected void setAllocationRepository(FundAllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    /** Maps fund NAV history to candle responses, preserving order. */
    public abstract List<FundCandleResponse> toFundCandleResponses(List<FundCandle> candles);

    /**
     * Maps a fund entity to the unified market-asset response, renaming code/price fields, tagging
     * the asset {@code type} as {@code FUND}, and nesting fund-specific fields under {@code metadata}.
     * {@code changeAmount} and {@code image} are intentionally left unset for funds.
     */
    @Mapping(target = "code", source = "fundCode")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "changeAmount", ignore = true)
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.FUND)")
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "metadata", source = "fund", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Fund fund);

    /** Maps a list of funds to unified market-asset responses, preserving order. */
    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Fund> funds);

    /**
     * Builds the fund-specific metadata block nested inside the unified asset response, including
     * type, portfolio/investor figures, risk, category ranking and trailing returns, plus the
     * asset-class allocation breakdown loaded (ordered by percentage descending) from
     * {@code allocationRepository}. Registered under the {@code "metadata"} qualifier.
     */
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
