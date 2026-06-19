package com.finance.market.stock.mapper;

import com.finance.market.core.mapper.MarketMetadataBuilder;


import com.finance.market.core.dto.response.CandleResponse;
import com.finance.market.core.dto.response.MarketAssetResponse;
import com.finance.shared.dto.response.StockMetadata;
import com.finance.market.stock.model.CompanyProfile;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockCandle;
import com.finance.market.stock.model.StockIndexMembership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;

/**
 * MapStruct mapper exposing {@link Stock} and {@link StockCandle} entities as the generic market
 * API response DTOs. Each asset response is tagged with {@code MarketType.STOCK} and carries a
 * stock-specific {@link StockMetadata} block assembled by {@link #buildMetadata(Stock)}.
 */
@Mapper(componentModel = "spring")
public abstract class StockResponseMapper implements MarketMetadataBuilder<Stock, StockMetadata> {

    /** Maps a list of stock candles to generic candle responses, preserving order. */
    public abstract List<CandleResponse> toStockCandleResponses(List<StockCandle> candles);

    /**
     * Maps a {@link Stock} to the unified market-asset response: the symbol becomes the asset code,
     * the current price and day change are copied over, the type is fixed to {@code STOCK}, and the
     * stock-specific metadata is attached.
     */
    @Mapping(target = "code", source = "symbol")
    @Mapping(target = "price", source = "currentPrice")
    @Mapping(target = "changeAmount", source = "changeAmount")
    @Mapping(target = "changePercent", source = "changePercent")
    @Mapping(target = "type", expression = "java(MarketType.STOCK)")
    @Mapping(target = "metadata", source = "stock", qualifiedByName = "metadata")
    public abstract MarketAssetResponse toMarketAssetResponse(Stock stock);

    /** Maps a list of stocks to unified market-asset responses, preserving order. */
    public abstract List<MarketAssetResponse> toMarketAssetResponses(List<Stock> stocks);

    /**
     * Builds the stock-specific metadata block (segment, volume, exchange, open/high/low) embedded
     * in each asset response. Used as the {@code "metadata"} qualifier for {@code toMarketAssetResponse}.
     */
    @Override
    @Named("metadata")
    public StockMetadata buildMetadata(Stock stock) {
        return new StockMetadata(
                stock.getStockSegment(),
                stock.getVolume(),
                stock.getExchange(),
                stock.getOpenPrice(),
                stock.getDayHigh(),
                stock.getDayLow(),
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    /**
     * Detail-only metadata: the base stock fields plus the company künye, the indices the stock belongs to
     * ({@code memberships}) and — when the asset is itself an index — the member stocks that make it up
     * ({@code constituents}). Used by the single-asset detail path; list responses keep the lean block.
     */
    public StockMetadata buildDetailMetadata(Stock stock, CompanyProfile profile,
                                             List<StockIndexMembership> memberships,
                                             List<StockIndexMembership> constituents,
                                             Map<String, String> constituentNames) {
        List<StockMetadata.IndexMembership> indexMemberships = memberships.stream()
                .map(m -> new StockMetadata.IndexMembership(m.getId().getIndexCode(), m.getWeight()))
                .toList();
        List<StockMetadata.IndexConstituent> indexConstituents = constituents.stream()
                .map(m -> new StockMetadata.IndexConstituent(
                        m.getId().getStockSymbol(),
                        m.getWeight(),
                        constituentNames.get(m.getId().getStockSymbol())))
                .toList();
        return new StockMetadata(
                stock.getStockSegment(),
                stock.getVolume(),
                stock.getExchange(),
                stock.getOpenPrice(),
                stock.getDayHigh(),
                stock.getDayLow(),
                profile != null ? profile.getSector() : null,
                profile != null ? profile.getFoundedDate() : null,
                profile != null ? profile.getCity() : null,
                indexMemberships,
                indexConstituents
        );
    }
}
