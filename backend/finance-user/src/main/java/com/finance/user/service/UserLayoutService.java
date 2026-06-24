package com.finance.user.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.mapper.UserLayoutMapper;
import com.finance.user.model.UserLayout;
import com.finance.user.repository.UserLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads and saves a user's dashboard layout. Reads return an empty layout when none is stored; saves
 * run the overview payload through all registered {@link OverviewSaveSanitizer}s before persisting.
 */
@Service
@RequiredArgsConstructor
public class UserLayoutService {

    private final UserLayoutRepository repository;
    private final UserLayoutMapper mapper;
    private final List<OverviewSaveSanitizer> sanitizers;

    /** The user's stored layout, or an empty layout when none has been saved yet. */
    @Transactional(readOnly = true)
    public UserLayoutResponse getOrEmpty(String userSub) {
        return repository.findById(userSub)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(UserLayout.emptyFor(userSub)));
    }

    /** Sanitizes the overview through the configured chain, then upserts and flushes the user's layout. */
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
