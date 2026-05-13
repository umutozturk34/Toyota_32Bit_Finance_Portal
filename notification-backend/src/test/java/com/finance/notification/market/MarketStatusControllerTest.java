package com.finance.notification.market;

import com.finance.notification.market.session.MarketSession;
import com.finance.notification.market.session.MarketSessionResolver;
import com.finance.notification.market.session.SessionMarket;

import com.finance.common.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketStatusControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-05T11:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock private MarketSessionResolver resolver;
    @Mock private com.finance.common.i18n.Translator translator;

    private MarketStatusController controller() {
        return new MarketStatusController(resolver, FIXED_CLOCK, translator);
    }

    @Test
    void should_returnSnapshotOnlyForConfiguredMarkets_when_listingAll() {
        lenient().when(resolver.resolve(any(), any())).thenReturn(Optional.empty());
        lenient().when(resolver.resolve(eq(SessionMarket.STOCK), any())).thenReturn(Optional.of(MarketSession.OPEN));
        lenient().when(resolver.resolve(eq(SessionMarket.CRYPTO), any())).thenReturn(Optional.of(MarketSession.OPEN));
        lenient().when(resolver.nextTransition(eq(SessionMarket.STOCK), any()))
                .thenReturn(Optional.of(Instant.parse("2026-05-05T15:00:00Z")));
        lenient().when(resolver.nextTransition(eq(SessionMarket.CRYPTO), any())).thenReturn(Optional.empty());

        ApiResponse<List<MarketStatusResponse>> response = controller().listAll();

        assertThat(response.getData())
                .extracting(MarketStatusResponse::market)
                .containsExactlyInAnyOrder(SessionMarket.STOCK, SessionMarket.CRYPTO);
    }

    @Test
    void should_omitMarketsWithoutSchedule_when_resolverReturnsEmpty() {
        when(resolver.resolve(any(), any())).thenReturn(Optional.empty());

        ApiResponse<List<MarketStatusResponse>> response = controller().listAll();

        assertThat(response.getData()).isEmpty();
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void should_includeNextTransitionWhenPresentAndOmitWhenAbsent_when_listing() {
        lenient().when(resolver.resolve(any(), any())).thenReturn(Optional.empty());
        lenient().when(resolver.resolve(eq(SessionMarket.FUND), any())).thenReturn(Optional.of(MarketSession.CLOSED));
        lenient().when(resolver.nextTransition(eq(SessionMarket.FUND), any()))
                .thenReturn(Optional.of(Instant.parse("2026-05-06T06:30:00Z")));

        ApiResponse<List<MarketStatusResponse>> response = controller().listAll();

        MarketStatusResponse fund = response.getData().stream()
                .filter(m -> m.market() == SessionMarket.FUND)
                .findFirst()
                .orElseThrow();
        assertThat(fund.session()).isEqualTo(MarketSession.CLOSED);
        assertThat(fund.nextTransitionAt()).isEqualTo(Instant.parse("2026-05-06T06:30:00Z"));
    }
}
