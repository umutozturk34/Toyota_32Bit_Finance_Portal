package com.finance.market.core.dto.response;

import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import lombok.Builder;
import lombok.Value;

/**
 * Read-only view of a tracked asset returned to clients. Exposes the asset's type, code, display
 * name, the Binance symbol used for crypto pricing, its {@link StockSegment}, the
 * {@code indexAsset}/{@code compareOnly} flags that drive UI placement, the {@code sortOrder}
 * that fixes its position in listings, and the {@code enabled} flag the admin toggles to hide an
 * asset from the public lists without deleting it. Immutable and constructed via the generated builder.
 */
@Value
@Builder
public class TrackedAssetResponse {
    TrackedAssetType assetType;
    String assetCode;
    String displayName;
    String binanceSymbol;
    StockSegment stockSegment;
    boolean indexAsset;
    boolean compareOnly;
    Integer sortOrder;
    boolean enabled;
}
