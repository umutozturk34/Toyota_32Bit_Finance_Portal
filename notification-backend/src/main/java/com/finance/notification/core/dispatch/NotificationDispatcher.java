package com.finance.notification.core.dispatch;

import com.finance.notification.core.mail.MailSender;
import com.finance.notification.core.mapper.NotificationMapper;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.core.repository.NotificationRepository;
import com.finance.notification.user.UserPreferenceCacheService;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Log4j2
@Service
public class NotificationDispatcher {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserPreferenceCacheService userPreferenceCacheService;
    private final UserEmailLookup userEmailLookup;
    private final MailSender mailSender;
    private final NotificationStreamRegistry streamRegistry;
    private final NotificationMapper notificationMapper;
    private final Map<NotificationType, NotificationHandler> handlers;
    private final org.springframework.context.ApplicationEventPublisher events;

    public NotificationDispatcher(NotificationRepository notificationRepository,
                                  NotificationPreferenceRepository preferenceRepository,
                                  UserPreferenceCacheService userPreferenceCacheService,
                                  UserEmailLookup userEmailLookup,
                                  MailSender mailSender,
                                  NotificationStreamRegistry streamRegistry,
                                  NotificationMapper notificationMapper,
                                  List<NotificationHandler> handlerList,
                                  org.springframework.context.ApplicationEventPublisher events) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.userPreferenceCacheService = userPreferenceCacheService;
        this.userEmailLookup = userEmailLookup;
        this.mailSender = mailSender;
        this.streamRegistry = streamRegistry;
        this.notificationMapper = notificationMapper;
        this.events = events;
        this.handlers = new EnumMap<>(NotificationType.class);
        for (NotificationHandler h : handlerList) {
            this.handlers.put(h.type(), h);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(NotificationRequest request) {
        NotificationHandler handler = handlers.get(request.type());
        if (handler == null) {
            log.warn("No handler registered for type={}; dropping dispatch for user={}", request.type(), request.userSub());
            return;
        }

        RenderedNotification rendered = handler.render(request);
        NotificationPreference prefs = loadPreferences(request.userSub());

        if (prefs.wantsInApp(request.type())) {
            try {
                Notification persisted = notificationRepository.saveAndFlush(Notification.create(
                        request.userSub(),
                        request.type(),
                        rendered.title(),
                        rendered.body(),
                        request.payload().toMetadata(),
                        request.expiresAt()));
                log.info("In-app notification persisted id={} user={} type={}",
                        persisted.getId(), request.userSub(), request.type());
                streamRegistry.publish(request.userSub(), notificationMapper.toResponse(persisted));
            } catch (Exception ex) {
                log.error("Failed to persist in-app notification user={} type={} cause={}",
                        request.userSub(), request.type(), ex.getMessage(), ex);
                throw ex;
            }
        }

        if (prefs.wantsEmail(request.type())) {
            Optional<String> emailOpt = userEmailLookup.findEmail(request.userSub());
            if (emailOpt.isEmpty()) {
                log.debug("Email skipped (no address resolved) user={} type={}",
                        request.userSub(), request.type());
            } else {
                String theme = userPreferenceCacheService.resolveTheme(request.userSub());
                events.publishEvent(new EmailEnqueuedEvent(emailOpt.get(), theme, rendered));
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailEnqueued(EmailEnqueuedEvent event) {
        mailSender.send(event.to(), event.rendered().emailSubject(),
                event.rendered().emailTemplate(), event.rendered().emailModel(), event.theme());
    }

    private NotificationPreference loadPreferences(String userSub) {
        return preferenceRepository.findById(userSub)
                .orElseGet(() -> NotificationPreference.defaultsFor(userSub));
    }

    public record EmailEnqueuedEvent(String to, String theme, RenderedNotification rendered) {
    }
}
