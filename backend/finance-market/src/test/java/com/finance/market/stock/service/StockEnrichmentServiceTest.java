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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StockEnrichmentService}: the stale-gate, the stock path (company profile + size-index
 * weight upsert, no reconcile-delete) and the index path (constituent reconcile). AAA throughout; mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockEnrichmentServiceTest {

    @Mock private IsYatirimCompanyCardProvider companyCardProvider;
    @Mock private UzmanParaIndexConstituentProvider constituentProvider;
    @Mock private CompanyProfileRepository companyProfileRepository;
    @Mock private StockIndexMembershipRepository membershipRepository;
    @Mock private StockRepository stockRepository;

    private StockEnrichmentService service;

    @BeforeEach
    void setUp() {
        StockProperties props = new StockProperties();
        props.setProfileMaxAge(java.time.Duration.ofDays(7));
        service = new StockEnrichmentService(companyCardProvider, constituentProvider,
                companyProfileRepository, membershipRepository, stockRepository, props);
    }

    private Stock stock(String symbol, StockSegment segment) {
        return Stock.builder().symbol(symbol).stockSegment(segment).build();
    }

    private CompanyCardDto card() {
        return new CompanyCardDto("Garanti Bankası", "Ticari bankacılık", LocalDate.of(1946, 4, 25), "İSTANBUL",
                List.of(new CompanyCardDto.IndexWeight("XU030", new BigDecimal("2.5"))));
    }

    @Test
    void enrichIfStale_skipsScrape_whenProfileStillFresh() {
        // Arrange — a normal stock whose profile was refreshed an hour ago
        when(stockRepository.findById("GARAN.IS")).thenReturn(Optional.of(stock("GARAN.IS", StockSegment.EQUITY)));
        when(companyProfileRepository.findById("GARAN.IS")).thenReturn(Optional.of(
                CompanyProfile.builder().symbol("GARAN.IS").updatedAt(LocalDateTime.now().minusHours(1)).build()));

        // Act
        service.enrichIfStale("GARAN.IS");

        // Assert
        verify(companyCardProvider, never()).fetch(any());
        verify(companyProfileRepository, never()).save(any());
    }

    @Test
    void enrichIfStale_upsertsProfileAndSizeIndexWeight_withoutReconcileDelete() {
        // Arrange — missing profile forces a scrape
        when(stockRepository.findById("GARAN.IS")).thenReturn(Optional.of(stock("GARAN.IS", StockSegment.EQUITY)));
        when(companyProfileRepository.findById("GARAN.IS")).thenReturn(Optional.empty());
        when(companyCardProvider.fetch("GARAN.IS")).thenReturn(card());
        when(membershipRepository.findById(any())).thenReturn(Optional.empty());

        // Act
        service.enrichIfStale("GARAN.IS");

        // Assert — profile saved; the XU030 weight upserted; never a stock-level reconcile delete
        ArgumentCaptor<CompanyProfile> profile = ArgumentCaptor.forClass(CompanyProfile.class);
        verify(companyProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getSector()).isEqualTo("Ticari bankacılık");
        ArgumentCaptor<StockIndexMembership> membership = ArgumentCaptor.forClass(StockIndexMembership.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().getId().getIndexCode()).isEqualTo("XU030");
        assertThat(membership.getValue().getWeight()).isEqualByComparingTo("2.5");
        verify(membershipRepository, never()).deleteByIdStockSymbol(any());
    }

    @Test
    void enrichIfStale_keepsExistingData_whenScrapeReturnsNull() {
        // Arrange — stale profile but the scrape fails
        when(stockRepository.findById("GARAN.IS")).thenReturn(Optional.of(stock("GARAN.IS", StockSegment.EQUITY)));
        when(companyProfileRepository.findById("GARAN.IS")).thenReturn(Optional.of(
                CompanyProfile.builder().symbol("GARAN.IS").updatedAt(LocalDateTime.now().minusDays(30)).build()));
        when(companyCardProvider.fetch("GARAN.IS")).thenReturn(null);

        // Act
        service.enrichIfStale("GARAN.IS");

        // Assert
        verify(companyProfileRepository, never()).save(any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void enrichIfStale_reconcilesConstituents_forIndexAsset() {
        // Arrange — an index asset with no current (fresh) membership
        when(stockRepository.findById("XBANK.IS")).thenReturn(Optional.of(stock("XBANK.IS", StockSegment.SECONDARY_INDEX)));
        when(membershipRepository.findByIdIndexCodeOrderByWeightDesc("XBANK")).thenReturn(List.of());
        when(constituentProvider.fetchConstituents("XBANK")).thenReturn(List.of("GARAN", "AKBNK"));

        // Act
        service.enrichIfStale("XBANK.IS");

        // Assert — both members inserted into XBANK, no company-card scrape
        verify(companyCardProvider, never()).fetch(any());
        ArgumentCaptor<StockIndexMembership> inserted = ArgumentCaptor.forClass(StockIndexMembership.class);
        verify(membershipRepository, atLeastOnce()).save(inserted.capture());
        assertThat(inserted.getAllValues()).extracting(m -> m.getId().getStockSymbol())
                .containsExactlyInAnyOrder("GARAN.IS", "AKBNK.IS");
        assertThat(inserted.getAllValues()).allSatisfy(m ->
                assertThat(m.getId().getIndexCode()).isEqualTo("XBANK"));
    }

    @Test
    void enrichIfStale_skipsIndexScrape_whenConstituentsFresh() {
        // Arrange — index whose membership was reconciled recently
        when(stockRepository.findById("XBANK.IS")).thenReturn(Optional.of(stock("XBANK.IS", StockSegment.SECONDARY_INDEX)));
        when(membershipRepository.findByIdIndexCodeOrderByWeightDesc("XBANK")).thenReturn(List.of(
                StockIndexMembership.builder()
                        .id(new StockIndexMembership.Key("GARAN.IS", "XBANK"))
                        .updatedAt(LocalDateTime.now().minusHours(2))
                        .build()));

        // Act
        service.enrichIfStale("XBANK.IS");

        // Assert
        verify(constituentProvider, never()).fetchConstituents(any());
    }
}
