package com.finance.backend.dto.request;

import com.finance.backend.model.StockSegment;
import com.finance.backend.model.TrackedAssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

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

    private Boolean enabled = true;

    private StockSegment stockSegment;

    private Boolean indexAsset;

    private Boolean compareOnly;

    private Integer sortOrder = 0;
}
