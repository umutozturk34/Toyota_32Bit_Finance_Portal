package com.finance.notification.core.dispatch;

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

import java.time.LocalTime;
import java.time.ZoneId;
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

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(
                notificationRepository, preferenceRepository,
                userPreferenceCacheService, userEmailLookup, mailSender,
                List.of(systemHandler), events);
    }

    @Test
    void dispatch_dropsWhenHandlerMissing() {
        NotificationRequest request = NotificationRequest.of("u",
                NotificationType.PRICE_ALERT_FIRED, Map.of());

        dispatcher.dispatch(request);

        verify(notificationRepository, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void dispatch_persistsInAppWhenEnabled() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(true);
        prefs.setEmailSystem(false);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveZone("u")).thenReturn(ZoneId.of("UTC"));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(NotificationRequest.of("u", NotificationType.SYSTEM, Map.of("a", 1)));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Title");
        assertThat(captor.getValue().getMetadata()).containsEntry("a", 1);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void dispatch_skipsInAppWhenDisabled() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveZone("u")).thenReturn(ZoneId.of("UTC"));
        when(userPreferenceCacheService.resolveTheme("u")).thenReturn("DARK");
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.of("user@x.com"));

        dispatcher.dispatch(NotificationRequest.of("u", NotificationType.SYSTEM, Map.of()));

        verify(notificationRepository, never()).save(any());
        verify(events).publishEvent(any(NotificationDispatcher.EmailEnqueuedEvent.class));
    }

    @Test
    void dispatch_carriesThemeOnEmailEvent() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveZone("u")).thenReturn(ZoneId.of("UTC"));
        when(userPreferenceCacheService.resolveTheme("u")).thenReturn("LIGHT");
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.of("user@x.com"));

        dispatcher.dispatch(NotificationRequest.of("u", NotificationType.SYSTEM, Map.of()));

        org.mockito.ArgumentCaptor<NotificationDispatcher.EmailEnqueuedEvent> captor =
                org.mockito.ArgumentCaptor.forClass(NotificationDispatcher.EmailEnqueuedEvent.class);
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
        when(userPreferenceCacheService.resolveZone("u")).thenReturn(ZoneId.of("UTC"));

        dispatcher.dispatch(NotificationRequest.of("u", NotificationType.SYSTEM, Map.of()));

        verify(events, never()).publishEvent(any());
        verify(userEmailLookup, never()).findEmail(anyString());
    }

    @Test
    void dispatch_skipsEmailWhenLookupEmpty() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(false);
        prefs.setEmailSystem(true);
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveZone("u")).thenReturn(ZoneId.of("UTC"));
        when(userEmailLookup.findEmail("u")).thenReturn(Optional.empty());

        dispatcher.dispatch(NotificationRequest.of("u", NotificationType.SYSTEM, Map.of()));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void dispatch_skipsEmailDuringQuietHours() {
        NotificationPreference prefs = NotificationPreference.defaultsFor("u");
        prefs.setInappSystem(true);
        prefs.setEmailSystem(true);
        prefs.setQuietHoursStart(LocalTime.of(0, 0));
        prefs.setQuietHoursEnd(LocalTime.of(23, 59));
        when(preferenceRepository.findById("u")).thenReturn(Optional.of(prefs));
        when(userPreferenceCacheService.resolveZone("u")).thenReturn(ZoneId.of("UTC"));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(NotificationRequest.of("u", NotificationType.SYSTEM, Map.of()));

        verify(notificationRepository).save(any());
        verify(events, never()).publishEvent(any());
        verify(userEmailLookup, never()).findEmail(anyString());
    }

    @Test
    void dispatch_usesDefaultPreferencesWhenAbsent() {
        when(preferenceRepository.findById("u")).thenReturn(Optional.empty());
        when(userPreferenceCacheService.resolveZone("u")).thenReturn(ZoneId.of("UTC"));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        dispatcher.dispatch(NotificationRequest.of("u", NotificationType.SYSTEM, Map.of()));

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
