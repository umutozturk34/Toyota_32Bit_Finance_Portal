package com.finance.portfolio.dto.response;
import com.finance.portfolio.dto.response.PositionResponse;

import com.finance.portfolio.dto.response.PortfolioSummaryResponse;

import com.finance.common.dto.response.PagedResponse;

import com.finance.portfolio.dto.response.AllocationItem;

import com.finance.common.dto.response.PagedResponse;

import java.util.List;

public record PortfolioViewResponse(
        PortfolioSummaryResponse summary,
        PagedResponse<PositionResponse> positions,
        List<AllocationItem> allocation
) {}
