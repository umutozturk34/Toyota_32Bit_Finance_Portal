package com.finance.commodity.config;
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.commodity")
public class CommodityProperties {

    private String chartRange = "max";
    private String chartInterval = "1d";
    private int batchMinSample = 5;

    private Map<String, String> yahooSymbolOverrides = new HashMap<>(Map.of(
            "XAUTRY", "GC=F",
            "XAGTRY", "SI=F",
            "XPTTRY", "PL=F",
            "XPDTRY", "PA=F"
    ));

    private List<DerivativeRule> derivatives = new ArrayList<>(List.of(
            new DerivativeRule("XAUTRY", "XAUTRYG", new BigDecimal("31.1035")),
            new DerivativeRule("XAGTRY", "XAGTRYG", new BigDecimal("31.1035"))
    ));

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DerivativeRule {
        private String sourceCode;
        private String derivativeCode;
        private BigDecimal divisor;
    }
}
