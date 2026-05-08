package com.finance.market.fund.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TefasFundDto(
        String fundCode,
        String name,
        LocalDateTime date,
        BigDecimal price,
        BigDecimal bulletinPrice,
        BigDecimal shareCount,
        BigDecimal investorCount,
        BigDecimal portfolioSize
) {}
