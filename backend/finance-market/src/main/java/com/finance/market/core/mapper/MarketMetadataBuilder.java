package com.finance.market.core.mapper;

import com.finance.shared.dto.response.MarketAssetMetadata;

public interface MarketMetadataBuilder<T, M extends MarketAssetMetadata> {

    M buildMetadata(T entity);
}
