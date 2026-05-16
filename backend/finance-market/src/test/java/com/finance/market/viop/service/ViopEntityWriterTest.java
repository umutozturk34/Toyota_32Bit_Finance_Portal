package com.finance.market.viop.service;

import com.finance.common.model.MarketType;
import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.cache.MarketCacheService;
import com.finance.market.core.service.AssetRegistryService;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.ViopQuoteSnapshot;
import com.finance.market.viop.model.ViopCategory;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.model.ViopOptionSide;
import com.finance.market.viop.repository.ViopContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViopEntityWriterTest {

    @Mock private ViopContractRepository repository;
    @Mock private AssetRegistryService assetRegistry;
    @Mock private TrackedAssetCommandService trackedAssetCommand;
    @Mock private MarketCacheService<ViopContract> cacheService;

    private ViopEntityWriter writer;

    @BeforeEach
    void setUp() {
        ViopCategoryResolver categoryResolver = new ViopCategoryResolver();
        ViopSymbolParser symbolParser = new ViopSymbolParser();
        writer = new ViopEntityWriter(repository, assetRegistry, trackedAssetCommand,
                categoryResolver, symbolParser, cacheService);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ViopContractSpec futureSpec() {
        return ViopContractSpec.future("F_USDTRY0626", "USDTRY 2026-06-30", "USDTRY",
                LocalDate.of(2026, 6, 30), new BigDecimal("1000"), new BigDecimal("3500"),
                "Nakdi", "TRY");
    }

    private ViopContract existingContract(String symbol) {
        return ViopContract.builder()
                .symbol(symbol)
                .kind(ViopContractKind.FUTURE)
                .active(true)
                .build();
    }

    @Test
    void should_enrichExistingContractWithSpec_when_repositoryHasIt() {
        when(repository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(existingContract("F_USDTRY0626")));

        int enriched = writer.enrichSpecs(List.of(futureSpec()));

        assertThat(enriched).isEqualTo(1);
        verify(repository).save(any(ViopContract.class));
    }

    @Test
    void should_skipUnknownSpec_when_contractMissingFromRepository() {
        when(repository.findBySymbol("F_UNKNOWN")).thenReturn(Optional.empty());
        ViopContractSpec spec = ViopContractSpec.future("F_UNKNOWN", "X", "X",
                LocalDate.of(2026, 6, 30), null, null, null, "TRY");

        int enriched = writer.enrichSpecs(List.of(spec));

        assertThat(enriched).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void should_returnEmptySet_when_bulkSnapshotsListIsNull() {
        Set<String> result = writer.applyBulkSnapshots(null);

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnEmptySet_when_bulkSnapshotsListIsEmpty() {
        Set<String> result = writer.applyBulkSnapshots(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void should_deactivateContracts_when_activeButNotInRefreshSet() {
        ViopContract active1 = ViopContract.builder().symbol("F_X").active(true).build();
        ViopContract active2 = ViopContract.builder().symbol("F_Y").active(true).build();
        when(repository.findAll()).thenReturn(List.of(active1, active2));

        int deactivated = writer.deactivateNotIn(Set.of("F_X"));

        assertThat(deactivated).isEqualTo(1);
        assertThat(active1.isActive()).isTrue();
        assertThat(active2.isActive()).isFalse();
    }

    @Test
    void should_skipSave_when_noContractsDeactivated() {
        ViopContract active = ViopContract.builder().symbol("F_X").active(true).build();
        when(repository.findAll()).thenReturn(List.of(active));

        int deactivated = writer.deactivateNotIn(Set.of("F_X"));

        assertThat(deactivated).isZero();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void should_throwIllegalState_when_applySnapshotSymbolUnparseable() {
        ViopQuoteSnapshot snap = org.mockito.Mockito.mock(ViopQuoteSnapshot.class);
        when(repository.findBySymbol("BAD_SYMBOL")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.applySnapshot("BAD_SYMBOL", snap))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_markExpiredContractsInactive_when_repositoryReturnsExpired() {
        ViopContract c1 = ViopContract.builder().symbol("F_X").active(true)
                .expiryDate(LocalDate.of(2020, 1, 1)).build();
        when(repository.findExpired(LocalDate.of(2026, 5, 1))).thenReturn(List.of(c1));

        int expired = writer.markExpired(LocalDate.of(2026, 5, 1));

        assertThat(expired).isEqualTo(1);
        assertThat(c1.isActive()).isFalse();
        verify(repository).saveAll(any());
    }

    @Test
    void should_returnZero_when_noExpiredContractsExist() {
        when(repository.findExpired(LocalDate.of(2026, 5, 1))).thenReturn(List.of());

        int expired = writer.markExpired(LocalDate.of(2026, 5, 1));

        assertThat(expired).isZero();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void should_backfillDisplayName_when_existingContractMissingItDuringSnapshot() {
        ViopContract entity = ViopContract.builder()
                .symbol("O_ISCTRE0526P14.00")
                .kind(ViopContractKind.OPTION)
                .underlying("ISCTR")
                .optionSide(ViopOptionSide.PUT)
                .strikePrice(new BigDecimal("14.00"))
                .expiryDate(LocalDate.of(2026, 5, 31))
                .active(true)
                .build();
        when(repository.findBySymbol("O_ISCTRE0526P14.00")).thenReturn(Optional.of(entity));
        ViopQuoteSnapshot snap = org.mockito.Mockito.mock(ViopQuoteSnapshot.class);

        writer.applySnapshot("O_ISCTRE0526P14.00", snap);

        assertThat(entity.getDisplayName()).isEqualTo("ISCTR Put 14 · 31 May 26");
    }

    @Test
    void should_preserveExistingDisplayName_when_alreadyPopulatedDuringSnapshot() {
        ViopContract entity = ViopContract.builder()
                .symbol("O_HALKBE0526P41.00")
                .kind(ViopContractKind.OPTION)
                .underlying("HALKB")
                .optionSide(ViopOptionSide.PUT)
                .strikePrice(new BigDecimal("41.00"))
                .expiryDate(LocalDate.of(2026, 5, 31))
                .displayName("HALKB Put 41 · 31 May 26")
                .active(true)
                .build();
        when(repository.findBySymbol("O_HALKBE0526P41.00")).thenReturn(Optional.of(entity));
        ViopQuoteSnapshot snap = org.mockito.Mockito.mock(ViopQuoteSnapshot.class);

        writer.applySnapshot("O_HALKBE0526P41.00", snap);

        assertThat(entity.getDisplayName()).isEqualTo("HALKB Put 41 · 31 May 26");
    }
}
