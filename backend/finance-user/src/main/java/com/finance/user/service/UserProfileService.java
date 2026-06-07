package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.dto.ProfileResponse;
import com.finance.user.dto.ProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Reads and updates the user's profile (username, name, email) directly against Keycloak as the source of truth. */
@Log4j2
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final KeycloakAdminClient client;

    /** Reads the user's profile (username, first/last name, email) straight from Keycloak. */
    public ProfileResponse get(String userSub) {
        Map<String, Object> user = client.getUser(userSub);
        return new ProfileResponse(
                asString(user.get("username")),
                asString(user.get("firstName")),
                asString(user.get("lastName")),
                asString(user.get("email"))
        );
    }

    /** Updates username and first/last name in Keycloak (email is not changed), then returns the refreshed profile. */
    public ProfileResponse update(String userSub, ProfileUpdateRequest request) {
        client.updateBasics(userSub, request.username(), request.firstName(), request.lastName());
        log.info("Profile updated user={}", userSub);
        return get(userSub);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
