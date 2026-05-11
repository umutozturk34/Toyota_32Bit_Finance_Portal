package com.finance.user.service;

import com.finance.common.event.UserStatusChangedEvent;
import com.finance.common.exception.BusinessException;
import com.finance.common.repository.UserStatusRepository;
import com.finance.common.security.UserStatusPort;
import com.finance.shared.event.EventPublisherPort;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.dto.AdminUserResponse;
import com.finance.user.dto.KeycloakUser;
import com.finance.user.mapper.KeycloakUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final KeycloakAdminClient client;
    private final KeycloakUserMapper mapper;
    private final UserStatusRepository userStatusRepository;
    private final UserStatusPort userStatus;
    private final EventPublisherPort eventPublisher;

    public List<AdminUserResponse> listUsers(int first, int max, String search) {
        List<KeycloakUser> users = client.listUsers(first, max, search);
        if (users == null) return List.of();
        return users.stream().map(mapper::toResponse).toList();
    }

    public long countUsers(String search) {
        return client.countUsers(search);
    }

    public void banUser(String userId, String callerSub) {
        if (userId != null && userId.equals(callerSub)) {
            throw new BusinessException("error.admin.user.cannotBanSelf");
        }
        applyStatusChange(userId, false);
    }

    public void unbanUser(String userId) {
        applyStatusChange(userId, true);
    }

    private void applyStatusChange(String userId, boolean enabled) {
        client.setEnabled(userId, enabled);
        userStatusRepository.upsertEnabled(userId, enabled);
        userStatus.invalidate(userId);
        eventPublisher.publish(UserStatusChangedEvent.of(userId, enabled));
    }
}
