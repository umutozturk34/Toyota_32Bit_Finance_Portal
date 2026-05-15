package com.finance.market.bank.service;

import com.finance.market.bank.dto.BankRateSnapshot;
import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import com.finance.market.bank.port.BankRateProvider;
import com.finance.market.bank.repository.BankExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Log4j2
@Service
@RequiredArgsConstructor
public class BankRatesService {

    private final List<BankRateProvider> providers;
    private final BankExchangeRateRepository repository;

    @Transactional
    public int refreshAll() {
        LocalDateTime now = LocalDateTime.now();
        int total = 0;
        for (BankRateProvider provider : providers) {
            try {
                List<BankRateSnapshot> snaps = provider.fetchAll();
                for (BankRateSnapshot s : snaps) {
                    upsert(s, now);
                }
                total += snaps.size();
                log.info("BankRates {} persisted {} rows", provider.sourceId(), snaps.size());
            } catch (Exception e) {
                log.warn("BankRates provider {} failed: {}", provider.sourceId(), e.getMessage());
            }
        }
        return total;
    }

    private void upsert(BankRateSnapshot s, LocalDateTime now) {
        Optional<BankExchangeRate> existing = repository
                .findBySourceAndBankCodeAndCurrencyCode(s.source(), s.bankCode(), s.currencyCode());
        BankExchangeRate row = existing.orElseGet(() -> BankExchangeRate.builder()
                .source(s.source())
                .bankCode(s.bankCode())
                .currencyCode(s.currencyCode())
                .build());
        row.setBankName(s.bankName());
        row.setBankLogoUrl(s.bankLogoUrl());
        row.setCurrencyName(s.currencyName());
        row.setAssetKind(s.assetKind());
        row.setBuyRate(s.buyRate());
        row.setSellRate(s.sellRate());
        row.setCapturedAt(now);
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public List<BankExchangeRate> findByCurrency(String currencyCode) {
        return repository.findByCurrencyCodeOrderByBankNameAsc(currencyCode);
    }

    @Transactional(readOnly = true)
    public List<BankExchangeRate> findByKind(BankRateAssetKind kind) {
        return repository.findByAssetKindOrderByCurrencyCodeAscBankNameAsc(kind);
    }

    @Transactional(readOnly = true)
    public List<String> listCurrencyCodes(BankRateAssetKind kind) {
        return repository.findDistinctCurrencyCodesByAssetKind(kind);
    }
}
