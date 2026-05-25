package com.finance.notification.core.service;

import com.finance.notification.core.dto.NotificationPreferenceResponse;
import com.finance.notification.core.dto.NotificationPreferenceUpdateRequest;
import com.finance.notification.core.mapper.NotificationPreferenceMapper;
import com.finance.notification.core.model.NotificationPreference;
import com.finance.notification.core.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository repository;

    @Mock
    private NotificationPreferenceMapper mapper;

    @InjectMocks
    private NotificationPreferenceService service;

    @Test
    void getOrDefault_returnsStoredWhenPresent() {
        NotificationPreference stored = NotificationPreference.defaultsFor("user-1");
        stored.setEmailWatchlist(true);
        when(repository.findById("user-1")).thenReturn(Optional.of(stored));
        when(mapper.toResponse(stored)).thenReturn(stubResponseFrom(stored));

        NotificationPreferenceResponse result = service.getOrDefault("user-1");

        assertThat(result.emailWatchlist()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void getOrDefault_returnsDefaultsWhenAbsent() {
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        when(mapper.toResponse(any(NotificationPreference.class)))
                .thenAnswer(inv -> stubResponseFrom(inv.getArgument(0)));

        NotificationPreferenceResponse result = service.getOrDefault("user-1");

        assertThat(result.inappPriceAlerts()).isTrue();
        assertThat(result.emailWatchlist()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void upsert_appliesPartialRequestAndPersists() {
        NotificationPreferenceUpdateRequest request = new NotificationPreferenceUpdateRequest(
                null, null, null, true, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(repository.findById("user-1")).thenReturn(Optional.empty());
        when(repository.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(NotificationPreference.class)))
                .thenAnswer(inv -> stubResponseFrom(inv.getArgument(0)));

        service.upsert("user-1", request);

        ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserSub()).isEqualTo("user-1");
        assertThat(captor.getValue().isEmailWatchlist()).isTrue();
    }

    private NotificationPreferenceResponse stubResponseFrom(NotificationPreference p) {
        return new NotificationPreferenceResponse(
                p.isEmailEnabled(),
                p.isEmailPriceAlerts(), p.isInappPriceAlerts(),
                p.isEmailWatchlist(), p.isInappWatchlist(),
                p.isEmailSystem(), p.isInappSystem(),
                p.isEmailMarketOpened(), p.isInappMarketOpened(),
                p.isEmailMarketClosed(), p.isInappMarketClosed(),
                p.isEmailMarketDataUpdated(), p.isInappMarketDataUpdated(),
                p.isEmailNewsPublished(), p.isInappNewsPublished(),
                p.isEmailPortfolioUpdated(), p.isInappPortfolioUpdated(),
                p.isEmailMacroIndicators(), p.isInappMacroIndicators(),
                p.getMarketSessionMarkets());
    }

}
