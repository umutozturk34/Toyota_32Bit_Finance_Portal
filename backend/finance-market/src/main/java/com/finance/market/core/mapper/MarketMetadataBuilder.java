package com.finance.market.core.mapper;

import com.finance.shared.dto.response.MarketAssetMetadata;

/**
 * Mix-in for response mappers that build a market-specific metadata block from an entity.
 *
 * @param <T> the source entity
 * @param <M> the market's metadata type
 */
public interface MarketMetadataBuilder<T, M extends MarketAssetMetadata> {

    M buildMetadata(T entity);
}
