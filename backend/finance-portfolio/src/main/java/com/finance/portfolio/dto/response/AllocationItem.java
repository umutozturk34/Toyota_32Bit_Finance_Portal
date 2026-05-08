package com.finance.portfolio.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;

public record AllocationItem(
        String label,
        String assetType,
        BigDecimal valueTry,
        BigDecimal percent
) {}
