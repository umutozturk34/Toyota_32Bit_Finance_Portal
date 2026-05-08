package com.finance.market.bond.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import java.time.LocalDate;

public record BondSerieDto(
        String isin,
        String serieCode,
        String serieName,
        LocalDate maturityStart,
        LocalDate maturityEnd
) {}
