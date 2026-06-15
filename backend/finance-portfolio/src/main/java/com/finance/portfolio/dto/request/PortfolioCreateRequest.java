package com.finance.portfolio.dto.request;

import com.finance.portfolio.model.PortfolioType;
import jakarta.validation.constraints.Size;

/**
 * Request body to create or rename a portfolio. On create, {@code type} fixes the portfolio's kind
 * ({@link PortfolioType#SPOT} or {@link PortfolioType#FIXED}); it is ignored on rename. The type is
 * defaulted to {@code SPOT} when the client omits it, so older clients (and the rename endpoint, which
 * only carries a name) stay backward-compatible.
 */
public record PortfolioCreateRequest(@Size(max = 25) String name, PortfolioType type) {

    public PortfolioCreateRequest {
        if (type == null) {
            type = PortfolioType.SPOT;
        }
    }

    /** Backward-compatible factory for callers that only supply a name; the type defaults to {@code SPOT}. */
    public PortfolioCreateRequest(String name) {
        this(name, PortfolioType.SPOT);
    }
}
