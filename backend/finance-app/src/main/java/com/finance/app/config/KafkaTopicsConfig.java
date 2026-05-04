package com.finance.app.config;
import com.finance.common.service.MarketSnapshotProcessor;

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

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.Map;

@Configuration
public class KafkaTopicsConfig {

    public static final String MARKET_UPDATED_TOPIC = "market.updated";
    public static final String USER_PREFERENCES_UPDATED_TOPIC = "user.preferences.updated";

    @Bean
    public NewTopic marketUpdatedTopic() {
        return TopicBuilder.name(MARKET_UPDATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(24 * 60 * 60 * 1000L))
                .build();
    }

    @Bean
    public NewTopic userPreferencesUpdatedTopic() {
        return TopicBuilder.name(USER_PREFERENCES_UPDATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .configs(Map.of(
                        "cleanup.policy", "compact",
                        "min.cleanable.dirty.ratio", "0.1"
                ))
                .build();
    }
}
