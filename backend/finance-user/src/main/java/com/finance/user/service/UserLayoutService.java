package com.finance.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.user.dto.UserLayoutResponse;
import com.finance.user.mapper.UserLayoutMapper;
import com.finance.user.model.UserLayout;
import com.finance.user.repository.UserLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserLayoutService {

    private final UserLayoutRepository repository;
    private final UserLayoutMapper mapper;

    @Transactional(readOnly = true)
    public UserLayoutResponse getOrEmpty(String userSub) {
        return repository.findById(userSub)
                .map(mapper::toResponse)
                .orElseGet(() -> mapper.toResponse(UserLayout.emptyFor(userSub)));
    }

    @Transactional
    public UserLayoutResponse saveOverview(String userSub, JsonNode overview) {
        UserLayout layout = repository.findById(userSub)
                .orElseGet(() -> UserLayout.emptyFor(userSub));
        layout.setOverview(overview);
        return mapper.toResponse(repository.save(layout));
    }
}
