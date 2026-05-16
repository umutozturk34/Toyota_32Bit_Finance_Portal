package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import com.finance.market.bank.service.BankRatesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BankRatesControllerTest {

    @Mock private BankRatesService service;
    @Mock private Translator translator;

    private BankRatesController controller;

    @BeforeEach
    void setUp() {
        controller = new BankRatesController(service, translator);
        when(translator.translate(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private BankExchangeRate row(String bankCode, String currencyCode, BankRateAssetKind kind) {
        return BankExchangeRate.builder()
                .source("DOVIZ_COM")
                .bankCode(bankCode)
                .bankName(bankCode + " Bank")
                .currencyCode(currencyCode)
                .assetKind(kind)
                .buyRate(new BigDecimal("30.50"))
                .sellRate(new BigDecimal("30.80"))
                .build();
    }

    @Test
    void should_filterByCurrencyCode_when_currencyParamProvided() {
        when(service.findByCurrency("USD"))
                .thenReturn(List.of(row("AKBANK", "USD", BankRateAssetKind.CURRENCY)));

        ApiResponse<List<BankExchangeRate>> response = controller.list("USD", BankRateAssetKind.CURRENCY);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getCurrencyCode()).isEqualTo("USD");
        verify(service).findByCurrency("USD");
        verify(service, never()).findByKind(BankRateAssetKind.CURRENCY);
    }

    @Test
    void should_normaliseCurrencyToUpperCase_when_passingFilter() {
        when(service.findByCurrency("EUR")).thenReturn(List.of());

        controller.list("eur", BankRateAssetKind.CURRENCY);

        verify(service).findByCurrency("EUR");
    }

    @Test
    void should_fallBackToKindFilter_when_currencyIsNull() {
        when(service.findByKind(BankRateAssetKind.GOLD))
                .thenReturn(List.of(row("AKBANK", "GRAM_ALTIN", BankRateAssetKind.GOLD)));

        controller.list(null, BankRateAssetKind.GOLD);

        verify(service).findByKind(BankRateAssetKind.GOLD);
        verify(service, never()).findByCurrency(anyString());
    }

    @Test
    void should_fallBackToKindFilter_when_currencyIsBlank() {
        when(service.findByKind(BankRateAssetKind.CURRENCY)).thenReturn(List.of());

        controller.list("   ", BankRateAssetKind.CURRENCY);

        verify(service).findByKind(BankRateAssetKind.CURRENCY);
    }

    @Test
    void should_returnDistinctCurrencyCodes_when_currenciesEndpointCalled() {
        when(service.listCurrencyCodes(BankRateAssetKind.GOLD))
                .thenReturn(List.of("GRAM_ALTIN", "CEYREK_ALTIN"));

        ApiResponse<List<String>> response = controller.listCurrencies(BankRateAssetKind.GOLD);

        assertThat(response.getData()).containsExactly("GRAM_ALTIN", "CEYREK_ALTIN");
    }
}
