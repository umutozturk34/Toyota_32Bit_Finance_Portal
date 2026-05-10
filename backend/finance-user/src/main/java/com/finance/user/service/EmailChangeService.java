package com.finance.user.service;

import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.shared.event.EventPublisherPort;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.config.UserSecurityProperties;
import com.finance.user.model.EmailChangeRequest;
import com.finance.user.repository.EmailChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Log4j2
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    private final EmailChangeRequestRepository repository;
    private final KeycloakAdminClient keycloakClient;
    private final UserPreferenceService preferenceService;
    private final EventPublisherPort eventPublisher;
    private final UserSecurityProperties securityProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void initiate(String userSub, String newEmail) {
        String oldEmail = keycloakClient.getEmail(userSub);
        if (oldEmail == null) {
            throw new ResourceNotFoundException("error.email.currentNotFound");
        }
        if (newEmail.equalsIgnoreCase(oldEmail)) {
            throw new BadRequestException("error.email.sameAddress");
        }

        String code = generateCode();
        String hash = passwordEncoder.encode(code);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(securityProperties.emailChange().codeTtl());

        EmailChangeRequest request = repository.findById(userSub).orElseGet(EmailChangeRequest::new);
        boolean targetChanged = !newEmail.equalsIgnoreCase(request.getNewEmail());
        request.setUserSub(userSub);
        request.setNewEmail(newEmail);
        request.setCodeHash(hash);
        if (targetChanged) request.setAttempts(0);
        request.setExpiresAt(expiresAt);
        repository.save(request);

        String theme = preferenceService.getOrDefault(userSub).theme().name();

        eventPublisher.publish(new EmailChangeCodeRequestedEvent(
                UUID.randomUUID().toString(),
                userSub,
                oldEmail,
                newEmail,
                code,
                theme,
                expiresAt,
                OffsetDateTime.now()
        ));
        log.info("Email change initiated user={} expiresAt={}", userSub, expiresAt);
    }

    @Transactional
    public void confirm(String userSub, String code) {
        EmailChangeRequest request = repository.findById(userSub)
                .orElseThrow(() -> new BadRequestException("error.email.noActiveChange"));

        if (request.getExpiresAt().isBefore(OffsetDateTime.now())) {
            repository.delete(request);
            throw new BadRequestException("error.email.codeExpired");
        }
        if (request.getAttempts() >= securityProperties.emailChange().maxAttempts()) {
            repository.delete(request);
            throw new BadRequestException("error.email.tooManyAttempts");
        }
        if (!passwordEncoder.matches(code, request.getCodeHash())) {
            request.setAttempts(request.getAttempts() + 1);
            repository.save(request);
            int remaining = securityProperties.emailChange().maxAttempts() - request.getAttempts();
            throw new BadRequestException("error.email.invalidCode", remaining);
        }

        keycloakClient.setEmail(userSub, request.getNewEmail());
        repository.delete(request);
        log.info("Email change confirmed user={}", userSub);
    }

    @Transactional
    public void cancel(String userSub) {
        repository.findById(userSub).ifPresent(repository::delete);
    }

    public Optional<PendingState> currentPending(String userSub) {
        return repository.findById(userSub).map(r -> new PendingState(r.getNewEmail(), r.getExpiresAt()));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(securityProperties.emailChange().codeLength());
        for (int i = 0; i < securityProperties.emailChange().codeLength(); i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    public record PendingState(String newEmail, OffsetDateTime expiresAt) {
    }
}
