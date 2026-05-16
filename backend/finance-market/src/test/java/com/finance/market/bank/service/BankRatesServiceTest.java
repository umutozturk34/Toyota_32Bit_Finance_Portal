package com.finance.market.bank.service;

import com.finance.market.bank.dto.BankRateSnapshot;
import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import com.finance.market.bank.port.BankRateProvider;
import com.finance.market.bank.repository.BankExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankRatesServiceTest {

    @Mock private BankRateProvider provider;
    @Mock private BankExchangeRateRepository repository;

    private BankRatesService service;

    @BeforeEach
    void setUp() {
        service = new BankRatesService(List.of(provider), repository, null);
        java.lang.reflect.Field selfField;
        try {
            selfField = BankRatesService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(service, service);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BankRateSnapshot snap(String bankCode, String currency, BankRateAssetKind kind,
                                          String buy, String sell) {
        return new BankRateSnapshot(
                "DOVIZ_COM",
                bankCode,
                bankCode + " Bank",
                "https://logo.example/" + bankCode,
                currency,
                currency + " Name",
                kind,
                new BigDecimal(buy),
                new BigDecimal(sell));
    }

    @Test
    void should_insertNewRows_when_currencyIsUnknown() {
        when(provider.sourceId()).thenReturn("DOVIZ_COM");
        when(provider.fetchAll()).thenReturn(List.of(
                snap("AKBANK", "USD", BankRateAssetKind.CURRENCY, "30.0000", "30.5000"),
                snap("GARANTI", "USD", BankRateAssetKind.CURRENCY, "30.1000", "30.4000")));
        when(repository.findBySourceAndBankCodeAndCurrencyCode(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(BankExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        int persisted = service.refreshAll();

        assertThat(persisted).isEqualTo(2);
        ArgumentCaptor<BankExchangeRate> captor = ArgumentCaptor.forClass(BankExchangeRate.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(BankExchangeRate::getBankCode)
                .containsExactlyInAnyOrder("AKBANK", "GARANTI");
    }

    @Test
    void should_updateExistingRow_when_bankAndCurrencyAlreadyPresent() {
        BankExchangeRate existing = BankExchangeRate.builder()
                .source("DOVIZ_COM")
                .bankCode("AKBANK")
                .currencyCode("USD")
                .buyRate(new BigDecimal("29.0000"))
                .sellRate(new BigDecimal("29.5000"))
                .build();
        when(provider.sourceId()).thenReturn("DOVIZ_COM");
        when(provider.fetchAll()).thenReturn(List.of(
                snap("AKBANK", "USD", BankRateAssetKind.CURRENCY, "30.0000", "30.5000")));
        when(repository.findBySourceAndBankCodeAndCurrencyCode("DOVIZ_COM", "AKBANK", "USD"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(BankExchangeRate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.refreshAll();

        assertThat(existing.getBuyRate()).isEqualByComparingTo("30.0000");
        assertThat(existing.getSellRate()).isEqualByComparingTo("30.5000");
        assertThat(existing.getCapturedAt()).isNotNull();
    }

    @Test
    void should_swallowProviderFailures_when_oneProviderThrows() {
        when(provider.sourceId()).thenReturn("DOVIZ_COM");
        when(provider.fetchAll()).thenThrow(new RuntimeException("network down"));

        int persisted = service.refreshAll();

        assertThat(persisted).isZero();
        verify(repository, times(0)).save(any());
    }
}
