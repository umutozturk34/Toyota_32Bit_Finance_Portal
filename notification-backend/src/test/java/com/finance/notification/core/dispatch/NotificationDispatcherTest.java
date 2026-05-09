package com.finance.notification.core.dispatch;

import com.finance.notification.core.dispatch.email.UserEmailLookup;

import com.finance.common.model.MarketType;
import com.finance.notification.alert.model.AlertDirection;
import com.finance.notification.core.dispatch.payload.PriceAlertPayload;
import com.finance.notification.core.dispatch.payload.SystemPayload;
import com.finance.notification.core.mail.MailSender;
import com.finance.notification.core.model.Notification;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.model.NotificationType;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import com.finance.notification.core.repository.NotificationRepository;
import com.finance.notification.user.UserPreferenceCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private UserPreferenceCacheService userPreferenceCacheService;

    @Mock
    private UserEmailLookup userEmailLookup;

    @Mock
    private MailSender mailSender;

    @Mock
    private NotificationStreamRegistry streamRegistry;

    @Mock
    private com.finance.notification.core.mapper.NotificationMapper notificationMapper;

    @Mock
    private ApplicationEventPublisher events;

    private NotificationDispatcher dispatcher;

    private final NotificationHandler systemHandler = new NotificationHandler() {
        @Override
        public NotificationType type() {
            return NotificationType.SYSTEM;
        }

        @Override
        public RenderedNotification render(NotificationRequest request) {
            return new RenderedNotification("Title", "Body",
                    "Subject", "system", Map.of("k", "v"));
        }
    };

    private static SystemPayload systemPayload() {
        return new SystemPayload("title", "body", null);
    }

    private static PriceAlertPayload priceAlertPayload() {
        return new PriceAlertPayload(1L, MarketType.CRYPTO, "btc", AlertDirection.ABOVE,
                BigDecimal.ONE, BigDecimal.TEN, null, null);
    }

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(
                notificationRepository, preferenceRepository,
                userPreferenceCacheService, userEmailLookup, mailSender,
                streamRegistry, notificationMapper,
                List.of(systemHandler), events);
    }

    @Test
    void dispatch_dropsWhenHandlerMissing() {
        dispatcher.dispatch(NotificationRequest.of("u", priceAlertPayload()));

        verify(notificationRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void dispatch_persistsInAppWhenEnabled() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(true);
        prefs.setEmailSystem(false);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        assertThat(captor.getValue().getMetadata()).containsEntry("title", "title");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void dispatch_skipsInAppWhenDisabled() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveTheme("u")).thenReturn("DARK");
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.of("user@x.com"));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        verify(notificationRepository, never()).save(any());
        verify(events).publishEvent(any(NotificationDispatcher.EmailEnqueuedEvent.class));
    }

    @Test
    void dispatch_carriesThemeOnEmailEvent() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveTheme("u")).thenReturn("LIGHT");
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.of("user@x.com"));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        ArgumentCaptor<NotificationDispatcher.EmailEnqueuedEvent> captor =
                ArgumentCaptor.forClass(NotificationDispatcher.EmailEnqueuedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().theme()).isEqualTo("LIGHT");
        assertThat(captor.getValue().rendered().emailModel()).doesNotContainKey("theme");
    }

    @Test
    void dispatch_skipsEmailWhenMasterSwitchOff() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setEmailEnabled(false);
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        verify(events, never()).publishEvent(any());
        verify(userEmailLookup, never()).findEmail(anyString());
    }

    @Test
    void dispatch_skipsEmailWhenLookupEmpty() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.empty());

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void dispatch_usesDefaultPreferencesWhenAbsent() {
        when(preferenceRepository.findById("u")).thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(NotificationRequest.of("u", systemPayload()));

        verify(notificationRepository).save(any());
    }

    @Test
    void onEmailEnqueued_invokesMailSenderWithTheme() {
        RenderedNotification rendered = new RenderedNotification("t", "b", "subj", "tpl",
                Map.of("k", "v"));

        dispatcher.onEmailEnqueued(new NotificationDispatcher.EmailEnqueuedEvent("to@x.com", "LIGHT", rendered));

        verify(mailSender).send("to@x.com", "subj", "tpl", Map.of("k", "v"), "LIGHT");
    }
}
