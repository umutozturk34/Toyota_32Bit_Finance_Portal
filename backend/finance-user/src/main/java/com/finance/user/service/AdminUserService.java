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
import java.util.Set;

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

    // Keycloak's built-in realm roles carry no product meaning, so they're hidden from the admin view — only the
    // app roles (USER/ADMIN) are shown. {@code default-roles-<realm>} is matched by prefix since it embeds the
    // realm name.
    private static final Set<String> DEFAULT_REALM_ROLES = Set.of("offline_access", "uma_authorization");
    private static final String DEFAULT_ROLES_PREFIX = "default-roles-";

    /**
     * Returns a page of users from Keycloak mapped to the admin response DTO; an empty list when
     * Keycloak yields none.
     *
     * @param first  zero-based offset of the first result
     * @param max    maximum number of users to return
     * @param search optional username/email filter, null for all
     */
    public List<AdminUserResponse> listUsers(int first, int max, String search) {
        List<KeycloakUser> users = client.listUsers(first, max, search);
        if (users == null) return List.of();
        return users.stream().map(u -> mapper.toResponse(u, appRoles(u.id()))).toList();
    }

    /**
     * The user's meaningful app roles (USER/ADMIN), with Keycloak's built-in defaults filtered out and the rest
     * sorted for a stable display. Empty for a null id or a user with no realm roles.
     */
    private List<String> appRoles(String userId) {
        if (userId == null) return List.of();
        List<String> realmRoles = client.getRealmRoleNames(userId);
        if (realmRoles == null) return List.of();
        return realmRoles.stream()
                .filter(role -> !DEFAULT_REALM_ROLES.contains(role) && !role.startsWith(DEFAULT_ROLES_PREFIX))
                .sorted()
                .toList();
    }

    /**
     * Total number of users matching the optional search filter, for paging the admin list.
     *
     * @param search optional username/email filter, null to count all users
     */
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

    /** Re-enables a previously banned account in both Keycloak and the local user-status table. */
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
