package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Manages a user's TOTP two-factor credentials through Keycloak. Two-factor is considered configured
 * when the user has at least one OTP credential; disabling removes them all, and individual devices
 * can be listed or removed by credential id.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final String OTP_CREDENTIAL_TYPE = "otp";

    private final KeycloakAdminClient client;

    /** Reports whether two-factor is enabled, i.e. the user has at least one OTP credential. */
    public TwoFactorStatus status(String userSub) {
        return new TwoFactorStatus(countOtpCredentials(userSub) > 0);
    }

    /** Removes every OTP credential for the user, returning how many were deleted. */
    public int disable(String userSub) {
        List<Map<String, Object>> credentials = client.listCredentials(userSub);
        int removed = 0;
        for (Map<String, Object> credential : credentials) {
            if (!OTP_CREDENTIAL_TYPE.equals(credential.get("type"))) continue;
            Object id = credential.get("id");
            if (id == null) continue;
            client.deleteCredential(userSub, id.toString());
            removed++;
        }
        log.info("Disabled 2FA for user={} removed={} credential(s)", userSub, removed);
        return removed;
    }

    /** Lists the user's registered OTP devices (id, label, creation time), filtering out non-OTP credentials. */
    public List<TwoFactorDevice> devices(String userSub) {
        return client.listCredentials(userSub).stream()
                .filter(c -> OTP_CREDENTIAL_TYPE.equals(c.get("type")))
                .map(c -> new TwoFactorDevice(
                        asString(c.get("id")),
                        asString(c.get("userLabel")),
                        asLong(c.get("createdDate"))))
                .toList();
    }

    /** Removes a single OTP device by its Keycloak credential id (leaves any other devices intact). */
    public void removeDevice(String userSub, String credentialId) {
        client.deleteCredential(userSub, credentialId);
        log.info("2FA device removed user={} credentialId={}", userSub, credentialId);
    }

    private long countOtpCredentials(String userSub) {
        return client.listCredentials(userSub).stream()
                .filter(c -> OTP_CREDENTIAL_TYPE.equals(c.get("type")))
                .count();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return null;
    }

    /** Whether the user has at least one OTP credential configured. */
    public record TwoFactorStatus(boolean configured) {
    }

    /** A single registered OTP device: Keycloak credential id, user-assigned label, and creation epoch-millis. */
    public record TwoFactorDevice(String id, String label, Long createdAt) {
    }
}
