package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import com.finance.market.bank.service.BankRatesService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated read API for bank exchange/asset rates, filterable by currency or by rate asset kind. */
@RestController
@RequestMapping("/api/v1/bank-rates")
@RequiredArgsConstructor
@Validated
public class BankRatesController {

    private final BankRatesService service;
    private final Translator translator;

    // Accepts both 3-letter ISO currency codes (USD) and the longer gold/asset codes (GRAM_ALTIN,
    // AYAR_22_BILEZIK, CUMHURIYET_ALTINI): letters, digits and underscore, length-capped. The original
    // letters-only {3} pattern rejected every gold code, so gold rate lookups failed validation with a 400.
    static final String CURRENCY_CODE_PATTERN = "^[A-Za-z0-9_]{1,32}$";

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<BankExchangeRate>> list(
            @RequestParam(required = false) @Pattern(regexp = CURRENCY_CODE_PATTERN) String currency,
            @RequestParam(required = false, defaultValue = "CURRENCY") BankRateAssetKind kind) {
        List<BankExchangeRate> rows = (currency != null && !currency.isBlank())
                ? service.findByCurrency(currency.toUpperCase())
                : service.findByKind(kind);
        return ApiResponse.success(translator.translate("api.bankRates.retrieved"), rows);
    }

    @GetMapping("/currencies")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<String>> listCurrencies(
            @RequestParam(required = false, defaultValue = "CURRENCY") BankRateAssetKind kind) {
        return ApiResponse.success(translator.translate("api.bankRates.currenciesRetrieved"),
                service.listCurrencyCodes(kind));
    }
}
