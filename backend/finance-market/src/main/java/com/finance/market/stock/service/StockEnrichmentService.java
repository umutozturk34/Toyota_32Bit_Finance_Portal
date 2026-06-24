package com.finance.market.stock.service;

import com.finance.common.model.StockSegment;
import com.finance.market.stock.client.IsYatirimCompanyCardProvider;
import com.finance.market.stock.client.UzmanParaIndexConstituentProvider;
import com.finance.market.stock.config.StockProperties;
import com.finance.market.stock.dto.external.CompanyCardDto;
import com.finance.market.stock.model.CompanyProfile;
import com.finance.market.stock.model.Stock;
import com.finance.market.stock.model.StockIndexMembership;
import com.finance.market.stock.repository.CompanyProfileRepository;
import com.finance.market.stock.repository.StockIndexMembershipRepository;
import com.finance.market.stock.repository.StockRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enriches a BIST stock alongside its regular refresh. Two complementary, conflict-free membership flows feed
 * the same {@code stock_index_membership} table:
 * <ul>
 *   <li>A normal stock → its İş Yatırım company künye (profile) plus the WEIGHT it carries in the size indices
 *       (XU030/050/100) from the company card, upserted in place (never deleting other rows).</li>
 *   <li>An index asset → its constituent stocks from a keyless index source, reconciled by index code while
 *       PRESERVING any weight a company-card run already set, so the two sources never clobber each other.</li>
 * </ul>
 * Stale-gated (re-scrapes only past {@link StockProperties#getProfileMaxAge()}), null-safe (a failed scrape
 * leaves existing data untouched) and transactional.
 */
@Log4j2
@Service
public class StockEnrichmentService {

    private final IsYatirimCompanyCardProvider companyCardProvider;
    private final UzmanParaIndexConstituentProvider constituentProvider;
    private final CompanyProfileRepository companyProfileRepository;
    private final StockIndexMembershipRepository membershipRepository;
    private final StockRepository stockRepository;
    private final Duration profileMaxAge;

    public StockEnrichmentService(IsYatirimCompanyCardProvider companyCardProvider,
                                  UzmanParaIndexConstituentProvider constituentProvider,
                                  CompanyProfileRepository companyProfileRepository,
                                  StockIndexMembershipRepository membershipRepository,
                                  StockRepository stockRepository,
                                  StockProperties stockProperties) {
        this.companyCardProvider = companyCardProvider;
        this.constituentProvider = constituentProvider;
        this.companyProfileRepository = companyProfileRepository;
        this.membershipRepository = membershipRepository;
        this.stockRepository = stockRepository;
        this.profileMaxAge = stockProperties.getProfileMaxAge();
    }

    /** Routes to index-constituent enrichment for an index asset, or company-profile enrichment for a stock. */
    @Transactional
    public void enrichIfStale(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        Stock stock = stockRepository.findById(symbol).orElse(null);
        if (stock != null && isIndex(stock)) {
            enrichIndexConstituents(symbol);
        } else {
            enrichCompanyProfile(symbol);
        }
    }

    // --- Normal stock: company künye + size-index weights ---------------------------------------------------

    private void enrichCompanyProfile(String symbol) {
        CompanyProfile existing = companyProfileRepository.findById(symbol).orElse(null);
        if (isFresh(existing != null ? existing.getUpdatedAt() : null)) return;

        CompanyCardDto card = companyCardProvider.fetch(symbol);
        if (card == null) return; // scrape failed — keep whatever we already have rather than wipe it

        LocalDateTime now = LocalDateTime.now();
        CompanyProfile profile = existing != null ? existing
                : CompanyProfile.builder().symbol(symbol).build();
        profile.setLegalName(card.legalName());
        profile.setSector(card.sector());
        profile.setFoundedDate(card.foundedDate());
        profile.setCity(card.city());
        profile.setUpdatedAt(now);
        companyProfileRepository.save(profile);

        // Upsert the size-index weights without deleting any rows, so the index-constituent flow's memberships
        // (and any sector membership) are never clobbered by this stock's enrichment.
        for (CompanyCardDto.IndexWeight weight : card.indexWeights()) {
            StockIndexMembership.Key key = new StockIndexMembership.Key(symbol, weight.indexCode());
            StockIndexMembership row = membershipRepository.findById(key)
                    .orElseGet(() -> StockIndexMembership.builder().id(key).build());
            row.setWeight(weight.weight());
            row.setUpdatedAt(now);
            membershipRepository.save(row);
        }
    }

    // --- Index asset: constituent stocks --------------------------------------------------------------------

    private void enrichIndexConstituents(String indexSymbol) {
        String indexCode = stripSuffix(indexSymbol);
        List<StockIndexMembership> existing = membershipRepository.findByIdIndexCodeOrderByWeightDesc(indexCode);
        if (isFresh(newestUpdate(existing))) return;

        List<String> tickers = constituentProvider.fetchConstituents(indexCode);
        if (tickers.isEmpty()) return; // scrape failed — keep the existing constituent set rather than wipe it

        LocalDateTime now = LocalDateTime.now();
        Set<String> wantedSymbols = new HashSet<>();
        for (String ticker : tickers) {
            wantedSymbols.add(ticker.toUpperCase() + ".IS");
        }
        Set<String> existingSymbols = new HashSet<>();
        for (StockIndexMembership row : existing) {
            existingSymbols.add(row.getId().getStockSymbol());
        }

        // Reconcile: drop members no longer in the index, touch the ones that stay (preserving their weight),
        // and insert the newcomers with a null weight (a later company-card run fills the size-index weight in).
        List<StockIndexMembership> toDelete = existing.stream()
                .filter(row -> !wantedSymbols.contains(row.getId().getStockSymbol()))
                .toList();
        if (!toDelete.isEmpty()) {
            membershipRepository.deleteAll(toDelete);
        }
        for (StockIndexMembership row : existing) {
            if (wantedSymbols.contains(row.getId().getStockSymbol())) {
                row.setUpdatedAt(now);
                membershipRepository.save(row);
            }
        }
        for (String symbol : wantedSymbols) {
            if (!existingSymbols.contains(symbol)) {
                membershipRepository.save(StockIndexMembership.builder()
                        .id(new StockIndexMembership.Key(symbol, indexCode))
                        .updatedAt(now)
                        .build());
            }
        }
        log.debug("Enriched index {} with {} constituents", indexCode, wantedSymbols.size());
    }

    // --- helpers --------------------------------------------------------------------------------------------

    private boolean isFresh(LocalDateTime updatedAt) {
        return updatedAt != null && updatedAt.isAfter(LocalDateTime.now().minus(profileMaxAge));
    }

    private static LocalDateTime newestUpdate(List<StockIndexMembership> rows) {
        return rows.stream()
                .map(StockIndexMembership::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private static boolean isIndex(Stock stock) {
        return stock.getStockSegment() == StockSegment.MAIN_INDEX
                || stock.getStockSegment() == StockSegment.SECONDARY_INDEX;
    }

    private static String stripSuffix(String symbol) {
        int dot = symbol.indexOf('.');
        return dot > 0 ? symbol.substring(0, dot) : symbol;
    }
}
