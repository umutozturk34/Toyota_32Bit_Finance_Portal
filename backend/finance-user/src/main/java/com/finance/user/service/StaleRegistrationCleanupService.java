package com.finance.user.service;

import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.dto.KeycloakUser;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Periodically removes never-verified registrations once they age past the TTL. Keycloak persists a user the
 * moment the registration form is submitted (before the email code is even entered) and has no native expiry
 * for accounts that are never verified, so abandoned and fake sign-ups would otherwise accumulate forever and
 * keep their email/username reserved (duplicateEmailsAllowed is off). Deletion is deliberately conservative:
 * a user is removed only when Keycloak still reports the email as unverified AND its creation timestamp is
 * older than the cutoff, so an in-progress or already-verified account is never touched.
 */
@Log4j2
@Service
public class StaleRegistrationCleanupService {

    private final KeycloakAdminClient adminClient;
    private final boolean enabled;
    private final Duration ttl;
    private final int maxPerRun;

    public StaleRegistrationCleanupService(
            KeycloakAdminClient adminClient,
            @Value("${app.scheduler.user-cleanup.enabled:true}") boolean enabled,
            @Value("${app.scheduler.user-cleanup.ttl:20m}") String ttl,
            @Value("${app.scheduler.user-cleanup.max-per-run:200}") int maxPerRun) {
        this.adminClient = adminClient;
        this.enabled = enabled;
        // Parsed explicitly via DurationStyle rather than bound straight to a Duration parameter, so the value
        // resolves regardless of whether @Value's conversion service has the Duration converter registered.
        this.ttl = DurationStyle.detectAndParse(ttl);
        this.maxPerRun = maxPerRun;
    }

    @Scheduled(cron = "${app.scheduler.user-cleanup.cron}", zone = "${app.timezone}")
    public void purgeStaleUnverifiedRegistrations() {
        if (!enabled) {
            return;
        }
        long cutoff = System.currentTimeMillis() - ttl.toMillis();
        List<KeycloakUser> candidates = adminClient.listUnverifiedUsers(0, maxPerRun);
        int deleted = 0;
        for (KeycloakUser user : candidates) {
            if (!isPurgeable(user, cutoff)) {
                continue;
            }
            try {
                adminClient.deleteUser(user.id());
                deleted++;
            } catch (RuntimeException ex) {
                log.warn("Failed to delete stale unverified user id={}: {}", user.id(), ex.getMessage());
            }
        }
        boolean batchFull = candidates.size() == maxPerRun;
        if (deleted > 0 || batchFull) {
            log.info("Stale registration cleanup removed {} unverified user(s){}",
                    deleted, batchFull ? " (batch full, more may remain for the next run)" : "");
        }
    }

    /** Purgeable only when Keycloak still reports the email unverified and the account predates the cutoff; null/unknown fields are kept. */
    private boolean isPurgeable(KeycloakUser user, long cutoff) {
        return user.id() != null
                && Boolean.FALSE.equals(user.emailVerified())
                && user.createdTimestamp() != null
                && user.createdTimestamp() < cutoff;
    }
}
