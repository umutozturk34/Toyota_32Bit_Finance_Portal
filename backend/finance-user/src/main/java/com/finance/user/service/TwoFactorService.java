package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Log4j2
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final String OTP_CREDENTIAL_TYPE = "otp";

    private final KeycloakAdminClient client;

    public TwoFactorStatus status(String userSub) {
        return new TwoFactorStatus(countOtpCredentials(userSub) > 0);
    }

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

    private long countOtpCredentials(String userSub) {
        return client.listCredentials(userSub).stream()
                .filter(c -> OTP_CREDENTIAL_TYPE.equals(c.get("type")))
                .count();
    }

    public record TwoFactorStatus(boolean configured) {
    }
}
