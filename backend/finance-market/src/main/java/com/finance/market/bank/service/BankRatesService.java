package com.finance.market.bank.service;

import com.finance.market.bank.dto.BankRateSnapshot;
import com.finance.market.bank.model.BankExchangeRate;
import com.finance.market.bank.model.BankRateAssetKind;
import com.finance.market.bank.port.BankRateProvider;
import com.finance.market.bank.repository.BankExchangeRateRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Log4j2
@Service
public class BankRatesService {

    private final List<BankRateProvider> providers;
    private final BankExchangeRateRepository repository;
    private BankRatesService self;

    public BankRatesService(List<BankRateProvider> providers,
                             BankExchangeRateRepository repository,
                             @Lazy BankRatesService self) {
        this.providers = providers;
        this.repository = repository;
        this.self = self;
    }

    public int refreshAll() {
        LocalDateTime now = LocalDateTime.now();
        int total = 0;
        for (BankRateProvider provider : providers) {
            List<BankRateSnapshot> snaps;
            try {
                snaps = provider.fetchAll();
            } catch (Exception e) {
                log.warn("BankRates provider {} fetch failed: {}", provider.sourceId(), e.getMessage());
                continue;
            }
            int persisted = 0;
            for (BankRateSnapshot s : snaps) {
                try {
                    self.upsertInNewTransaction(s, now);
                    persisted++;
                } catch (Exception e) {
                    log.warn("BankRates {} row {}/{} skipped: {}", provider.sourceId(),
                            s.bankCode(), s.currencyCode(), e.getMessage());
                }
            }
            total += persisted;
            log.info("BankRates {} persisted {}/{} rows", provider.sourceId(), persisted, snaps.size());
        }
        return total;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertInNewTransaction(BankRateSnapshot s, LocalDateTime now) {
        upsert(s, now);
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
