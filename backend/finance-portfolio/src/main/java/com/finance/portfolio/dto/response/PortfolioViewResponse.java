package com.finance.portfolio.dto.response;

import com.finance.common.dto.response.PagedResponse;

import java.util.List;

public record PortfolioViewResponse(
        PortfolioSummaryResponse summary,
        PagedResponse<PositionResponse> positions,
        List<AllocationItem> allocation
) {}
