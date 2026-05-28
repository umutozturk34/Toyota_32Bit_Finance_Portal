package com.finance.portfolio.dto.response;

import java.util.List;

/** SSE payload reporting whether snapshot backfill is running for a portfolio and which assets are still pending. */
public record BackfillStatusResponse(
        boolean running,
        Long since,
        List<PendingAsset> pendingAssets
) {
    public record PendingAsset(String assetType, String assetCode) {}
}
