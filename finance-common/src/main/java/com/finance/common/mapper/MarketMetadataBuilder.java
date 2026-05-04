package com.finance.common.mapper;

import com.finance.common.dto.response.MarketAssetMetadata;

public interface MarketMetadataBuilder<T, M extends MarketAssetMetadata> {

    M buildMetadata(T entity);
}
