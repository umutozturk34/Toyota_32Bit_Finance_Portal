package com.finance.user.service;

import com.finance.common.event.EmailChangeCodeRequestedEvent;
import com.finance.common.event.EmailChangeEventPort;
import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.user.client.KeycloakAdminClient;
import com.finance.user.model.EmailChangeRequest;
import com.finance.user.repository.EmailChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Log4j2
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final int CODE_LENGTH = 6;

    private final EmailChangeRequestRepository repository;
    private final KeycloakAdminClient keycloakClient;
    private final UserPreferenceService preferenceService;
    private final EmailChangeEventPort eventPort;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void initiate(String userSub, String newEmail) {
        String oldEmail = keycloakClient.getEmail(userSub);
        if (oldEmail == null) {
            throw new ResourceNotFoundException("Mevcut e-posta adresi bulunamadı");
        }
        if (newEmail.equalsIgnoreCase(oldEmail)) {
            throw new BadRequestException("Yeni e-posta mevcut adresle aynı");
        }

        String code = generateCode();
        String hash = passwordEncoder.encode(code);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(CODE_TTL);

        EmailChangeRequest request = repository.findById(userSub).orElseGet(EmailChangeRequest::new);
        boolean targetChanged = !newEmail.equalsIgnoreCase(request.getNewEmail());
        request.setUserSub(userSub);
        request.setNewEmail(newEmail);
        request.setCodeHash(hash);
        if (targetChanged) request.setAttempts(0);
        request.setExpiresAt(expiresAt);
        repository.save(request);

        String theme = preferenceService.getOrDefault(userSub).theme().name();

        eventPort.publishEmailChangeCode(new EmailChangeCodeRequestedEvent(
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
                .orElseThrow(() -> new BadRequestException("Aktif e-posta değişikliği yok, akışı baştan başlat"));

        if (request.getExpiresAt().isBefore(OffsetDateTime.now())) {
            repository.delete(request);
            throw new BadRequestException("Kodun süresi doldu, akışı baştan başlat");
        }
        if (request.getAttempts() >= MAX_ATTEMPTS) {
            repository.delete(request);
            throw new BadRequestException("Çok fazla hatalı deneme, akışı baştan başlat");
        }
        if (!passwordEncoder.matches(code, request.getCodeHash())) {
            request.setAttempts(request.getAttempts() + 1);
            repository.save(request);
            int remaining = MAX_ATTEMPTS - request.getAttempts();
            throw new BadRequestException("Geçersiz kod (" + remaining + " hak kaldı)");
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
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    public record PendingState(String newEmail, OffsetDateTime expiresAt) {
    }
}
