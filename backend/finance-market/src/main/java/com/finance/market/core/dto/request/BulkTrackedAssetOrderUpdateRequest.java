package com.finance.market.core.dto.request;

import com.finance.common.model.TrackedAssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Request payload for reordering the tracked assets of a single asset type in one atomic call.
 * Carries the {@link TrackedAssetType} scope and the complete list of per-asset sort positions to
 * apply. The item list must be non-empty and every item is cascade-validated.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkTrackedAssetOrderUpdateRequest {

    @NotNull
    private TrackedAssetType assetType;

    @NotEmpty
    @Valid
    private List<TrackedAssetOrderItemRequest> items;
}
