package com.finance.backend.dto.response;

import java.util.List;

public record PortfolioViewResponse(
        PortfolioSummaryResponse summary,
        PagedResponse<PositionResponse> positions,
        List<AllocationItem> allocation
) {}
