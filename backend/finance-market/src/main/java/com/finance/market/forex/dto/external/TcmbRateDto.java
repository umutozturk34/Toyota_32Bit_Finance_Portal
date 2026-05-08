package com.finance.market.forex.dto.external;
import com.finance.common.dto.external.*;
import com.finance.common.dto.request.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TcmbRateDto(
        @JacksonXmlProperty(isAttribute = true, localName = "Kod")
        String currencyCode,
        @JacksonXmlProperty(localName = "CurrencyName")
        String currencyName,
        @JacksonXmlProperty(localName = "Isim")
        String currencyNameTr,
        @JacksonXmlProperty(localName = "Unit")
        int unit,
        @JacksonXmlProperty(localName = "ForexBuying")
        BigDecimal forexBuying,
        @JacksonXmlProperty(localName = "ForexSelling")
        BigDecimal forexSelling,
        @JacksonXmlProperty(localName = "BanknoteBuying")
        BigDecimal banknoteBuying,
        @JacksonXmlProperty(localName = "BanknoteSelling")
        BigDecimal banknoteSelling,
        @JacksonXmlProperty(localName = "CrossRateUSD")
        BigDecimal crossRateUsd,
        @JacksonXmlProperty(localName = "CrossRateOther")
        BigDecimal crossRateOther
) {}
