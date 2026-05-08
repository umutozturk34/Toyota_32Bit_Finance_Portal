package com.finance.market.forex.dto.external;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.market.core.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.market.core.scheduler.*;
import com.finance.common.event.*;
import com.finance.market.core.mapper.*;
import com.finance.common.repository.*;
import com.finance.market.core.client.*;

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
