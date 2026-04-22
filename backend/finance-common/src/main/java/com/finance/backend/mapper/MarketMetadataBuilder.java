package com.finance.backend.mapper;

import com.finance.backend.dto.response.MarketAssetMetadata;

public interface MarketMetadataBuilder<T, M extends MarketAssetMetadata> {

    M buildMetadata(T entity);
}
