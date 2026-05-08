package com.finance.market.bond.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BondSnapshotDto(
        String seriesCode,
        String isinCode,
        BigDecimal cleanPrice,
        BigDecimal couponRate,
        LocalDate maturityStart,
        LocalDate maturityEnd,
        String serieName
) {}
