package com.finance.user.service;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.mapper.UserLayoutMapper;
import com.finance.user.model.UserLayout;
import com.finance.user.repository.UserLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserLayoutService {

    private final UserLayoutRepository repository;
    private final UserLayoutMapper mapper;

    public UserLayoutResponse getOrEmpty(String userSub) {
        UserLayout layout = repository.findById(userSub).orElseGet(() -> UserLayout.emptyFor(userSub));
        return mapper.toResponse(layout);
    }

    public UserLayoutResponse saveOverview(String userSub, JsonNode overview) {
        UserLayout layout = repository.findById(userSub).orElseGet(() -> UserLayout.emptyFor(userSub));
        layout.setOverview(overview);
        layout.setUpdatedAt(Instant.now());
        return mapper.toResponse(repository.save(layout));
    }
}
