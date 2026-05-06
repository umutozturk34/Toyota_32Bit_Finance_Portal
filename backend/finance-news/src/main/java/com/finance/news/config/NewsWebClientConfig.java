package com.finance.news.config;
import com.finance.common.config.AppProperties;

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

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class NewsWebClientConfig {

    private final AppProperties appProperties;

    @Bean("newsWebClient")
    public WebClient newsWebClient(WebClient.Builder builder) {
        return builder
                .clone()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(appProperties.getHttp().getNewsMaxInMemorySizeMb() * 1024 * 1024))
                .build();
    }
}
