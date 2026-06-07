package com.finance.market.bond.dto.external;

import java.time.LocalDate;

/**
 * Identifying metadata for a bond series as supplied by the external provider, including its
 * ISIN, code/name and the maturity window.
 *
 * @param isin          the series' ISIN identifier
 * @param serieCode     the provider's series code
 * @param serieName     the human-readable series name
 * @param maturityStart start of the maturity window
 * @param maturityEnd   end of the maturity window
 */
public record BondSerieDto(
        String isin,
        String serieCode,
        String serieName,
        LocalDate maturityStart,
        LocalDate maturityEnd
) {}
