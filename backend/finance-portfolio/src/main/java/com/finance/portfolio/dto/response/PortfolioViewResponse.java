package com.finance.portfolio.dto.response;

import com.finance.common.dto.response.PagedResponse;

import java.util.List;

/** Composite single-call view bundling summary, paged positions and allocation; sections are null when not requested. */
public record PortfolioViewResponse(
        PortfolioSummaryResponse summary,
        PagedResponse<PositionResponse> positions,
        List<AllocationItem> allocation
) {}
