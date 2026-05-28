package com.finance.app.controller;

import com.finance.common.dto.ApiResponse;
import com.finance.common.i18n.Translator;
import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import com.finance.market.bank.service.BankRatesService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated read API for bank exchange/asset rates, filterable by currency or by rate asset kind. */
@RestController
@RequestMapping("/api/v1/bank-rates")
@RequiredArgsConstructor
public class BankRatesController {

    private final BankRatesService service;
    private final Translator translator;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<BankExchangeRate>> list(
            @RequestParam(required = false) String currency,
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
