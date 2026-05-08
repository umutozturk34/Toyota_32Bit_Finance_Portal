package com.finance.market.fund.dto.internal;
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
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.finance.market.fund.dto.external.deserializer.SafeBigDecimalDeserializer;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TefasResponse(
        String errorCode,
        String errorMessage,
        List<FundData> resultList,
        Integer toplamSayi,
        Integer toplamSayfa
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FundData(
            String fonKodu,
            String fonUnvan,
            String tarih,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal fiyat,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal borsaBultenFiyat,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal tedPaySayisi,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal kisiSayisi,
            @JsonDeserialize(using = SafeBigDecimalDeserializer.class) BigDecimal portfoyBuyukluk,
            Integer rn
    ) {}
}
