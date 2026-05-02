package com.finance.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.backend.dto.UserLayoutResponse;
import com.finance.backend.mapper.UserLayoutMapper;
import com.finance.backend.model.UserLayout;
import com.finance.backend.repository.UserLayoutRepository;
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
