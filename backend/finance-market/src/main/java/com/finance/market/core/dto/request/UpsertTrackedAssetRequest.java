package com.finance.market.core.dto.request;

import com.finance.common.model.StockSegment;
import com.finance.common.model.TrackedAssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Request payload to create or update a tracked asset. The asset is identified by the required
 * {@link TrackedAssetType} and asset code; remaining fields are optional overrides such as the
 * display name, the Binance trading symbol used for crypto pricing, the {@link StockSegment}, and
 * the {@code indexAsset}/{@code compareOnly} flags that govern how the asset surfaces in the UI.
 * {@code sortOrder} controls list position and defaults to {@code 0}.
 */
@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class UpsertTrackedAssetRequest {

    @NotNull
    private TrackedAssetType assetType;

    @NotBlank
    private String assetCode;

    private String displayName;

    private String binanceSymbol;


    private StockSegment stockSegment;

    private Boolean indexAsset;

    private Boolean compareOnly;

    private Integer sortOrder = 0;
}
