package com.finance.backend.dto.external;
import java.math.BigDecimal;
public record TcmbRateDto(
        String currencyCode,
        String currencyName,
        String currencyNameTr,
        int unit,
        BigDecimal forexBuying,
        BigDecimal forexSelling,
        BigDecimal banknoteBuying,
        BigDecimal banknoteSelling,
        BigDecimal crossRateUsd,
        BigDecimal crossRateOther
) {}
