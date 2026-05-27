package com.finance.user.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.repository.UserStatusRepository;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.dto.AdminUserResponse;
import com.finance.user.dto.KeycloakUser;
import com.finance.user.mapper.KeycloakUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final KeycloakAdminClient client;
    private final KeycloakUserMapper mapper;
    private final UserStatusRepository userStatusRepository;

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
        log.info("Admin user status changed userId={} enabled={}", userId, enabled);
    }
}
