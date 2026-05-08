package com.finance.app.event;
import com.finance.common.event.KafkaTopics;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaUserPreferenceEventAdapter implements UserPreferenceEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishUserPreferencesUpdated(UserPreferencesUpdatedEvent event) {
        kafkaTemplate.send(KafkaTopics.USER_PREFERENCES_UPDATED, event.userSub(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish user.preferences.updated for {}: {}",
                                event.userSub(), ex.getMessage());
                    } else {
                        log.debug("Published user.preferences.updated for {}", event.userSub());
                    }
                });
    }
}
