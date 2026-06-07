package com.finance.market.core.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single entry in a bulk reorder request: binds one tracked asset (by code) to its desired
 * zero-based display position. The asset code must be present and the sort order non-negative.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackedAssetOrderItemRequest {

    @NotBlank
    private String assetCode;

    @NotNull
    @Min(0)
    private Integer sortOrder;
}
