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

/**
 * Admin operations over user accounts: listing/counting via Keycloak and ban/unban. Banning maps to
 * disabling the Keycloak account and mirroring the enabled flag into the local user-status table so
 * other modules can authorize without a Keycloak round-trip.
 */
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

    /** Disables a user account; an admin cannot ban their own account (guards against self-lockout). */
    public void banUser(String userId, String callerSub) {
        if (userId != null && userId.equals(callerSub)) {
            throw new BusinessException("error.admin.user.cannotBanSelf");
        }
        applyStatusChange(userId, false);
    }

    public void unbanUser(String userId) {
        applyStatusChange(userId, true);
    }

    /** Sets the enabled flag in Keycloak and mirrors it to the local user-status table in one operation. */
    private void applyStatusChange(String userId, boolean enabled) {
        client.setEnabled(userId, enabled);
        userStatusRepository.upsertEnabled(userId, enabled);
        log.info("Admin user status changed userId={} enabled={}", userId, enabled);
    }
}
