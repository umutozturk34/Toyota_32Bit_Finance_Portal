package com.finance.backend.service;

import com.finance.backend.client.KeycloakAdminClient;
import com.finance.backend.dto.AdminUserResponse;
import com.finance.backend.dto.KeycloakUser;
import com.finance.backend.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final KeycloakAdminClient client;

    public List<AdminUserResponse> listUsers(int first, int max, String search) {
        List<KeycloakUser> users = client.listUsers(first, max, search);
        if (users == null) return List.of();
        return users.stream().map(this::toResponse).toList();
    }

    public long countUsers(String search) {
        return client.countUsers(search);
    }

    public void banUser(String userId, String callerSub) {
        if (userId != null && userId.equals(callerSub)) {
            throw new BusinessException("You cannot ban your own account");
        }
        client.setEnabled(userId, false);
    }

    public void unbanUser(String userId) {
        client.setEnabled(userId, true);
    }

    private AdminUserResponse toResponse(KeycloakUser ku) {
        return new AdminUserResponse(
                ku.id(),
                ku.username(),
                ku.email(),
                ku.firstName(),
                ku.lastName(),
                ku.enabled() != null && ku.enabled(),
                ku.createdTimestamp() != null ? Instant.ofEpochMilli(ku.createdTimestamp()) : null
        );
    }
}
