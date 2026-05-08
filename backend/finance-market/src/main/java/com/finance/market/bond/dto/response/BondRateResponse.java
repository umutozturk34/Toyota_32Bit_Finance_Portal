package com.finance.market.bond.dto.response;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondRateResponse(
        LocalDate date,
        BigDecimal rate
) {}
