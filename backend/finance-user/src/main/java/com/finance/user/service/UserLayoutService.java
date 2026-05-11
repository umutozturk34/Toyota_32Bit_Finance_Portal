package com.finance.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.mapper.UserLayoutMapper;
import com.finance.user.model.UserLayout;
import com.finance.user.repository.UserLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserLayoutService {

    private final UserLayoutRepository repository;
    private final UserLayoutMapper mapper;
    private final List<OverviewSaveSanitizer> sanitizers;

    @Transactional(readOnly = true)
    public UserLayoutResponse getOrEmpty(String userSub) {
        return repository.findById(userSub)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(UserLayout.emptyFor(userSub)));
    }

    @Transactional
    public UserLayoutResponse saveOverview(String userSub, JsonNode overview) {
        JsonNode cleaned = overview != null ? overview : JsonNodeFactory.instance.objectNode();
        for (OverviewSaveSanitizer sanitizer : sanitizers) {
            cleaned = sanitizer.sanitize(cleaned);
        }
        UserLayout layout = repository.findById(userSub)
                .orElseGet(() -> UserLayout.emptyFor(userSub));
        layout.setOverview(cleaned);
        layout.setUpdatedAt(java.time.Instant.now());
        return mapper.toResponse(repository.saveAndFlush(layout));
    }
}
