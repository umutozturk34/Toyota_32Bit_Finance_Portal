package com.finance.portfolio.service;


import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioCrudServiceTest {

    private static final String USER_SUB = "user-1";
    private static final Long PORTFOLIO_ID = 7L;

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private com.finance.portfolio.repository.PortfolioDailySnapshotRepository dailySnapshotRepository;
    @Mock private com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private com.finance.portfolio.derivative.repository.DerivativePositionRepository derivativePositionRepository;
    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private PortfolioResponseMapper mapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.finance.market.core.service.CurrencyConverter currencyConverter;

    private final PortfolioProperties portfolioProperties = new PortfolioProperties();
    private PortfolioCrudService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioCrudService(portfolioRepository, positionRepository,
                dailySnapshotRepository, assetSnapshotRepository, derivativePositionRepository,
                trackedAssetRepository, mapper, eventPublisher, portfolioProperties, currencyConverter);
    }

    private TrackedAsset stubTrackedAsset(TrackedAssetType type, String code) {
        TrackedAsset asset = TrackedAsset.builder().id(1L).assetType(type).assetCode(code).build();
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(type, code))
                .thenReturn(Optional.of(asset));
        return asset;
    }

    @Test
    void shouldMapAllUserPortfoliosThroughMapper_whenListing() {
        Portfolio one = Portfolio.builder().id(1L).userSub(USER_SUB).name("Main").build();
        Portfolio two = Portfolio.builder().id(2L).userSub(USER_SUB).name("Alt").build();
        when(portfolioRepository.findByUserSub(USER_SUB)).thenReturn(List.of(one, two));
        when(mapper.toPortfolioResponse(one)).thenReturn(new PortfolioResponse(1L, "Main", null));
        when(mapper.toPortfolioResponse(two)).thenReturn(new PortfolioResponse(2L, "Alt", null));

        List<PortfolioResponse> responses = service.listPortfolios(USER_SUB);

        assertThat(responses).extracting(PortfolioResponse::name).containsExactly("Main", "Alt");
    }

    @Test
    void shouldSaveNewPortfolio_whenNameIsUnique() {
        when(portfolioRepository.findByUserSubAndName(USER_SUB, "New")).thenReturn(Optional.empty());
        Portfolio saved = Portfolio.builder().id(99L).userSub(USER_SUB).name("New").build();
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(saved);
        when(mapper.toPortfolioResponse(saved)).thenReturn(new PortfolioResponse(99L, "New", null));

        PortfolioResponse response = service.createPortfolio(USER_SUB, new PortfolioCreateRequest("New"));

        assertThat(response.id()).isEqualTo(99L);
        ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(captor.capture());
        assertThat(captor.getValue().getUserSub()).isEqualTo(USER_SUB);
        assertThat(captor.getValue().getName()).isEqualTo("New");
    }

    @Test
    void shouldThrowBusinessException_whenCreatingPortfolioWithDuplicateName() {
        Portfolio existing = Portfolio.builder().id(1L).userSub(USER_SUB).name("Main").build();
        when(portfolioRepository.findByUserSubAndName(USER_SUB, "Main")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createPortfolio(USER_SUB, new PortfolioCreateRequest("Main")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.duplicateName");
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void shouldThrowBusinessException_whenUserAlreadyOwnsMaxPortfolios() {
        when(portfolioRepository.countByUserSub(USER_SUB))
                .thenReturn((long) portfolioProperties.getMaxPortfoliosPerUser());

        assertThatThrownBy(() -> service.createPortfolio(USER_SUB, new PortfolioCreateRequest("Extra")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("error.portfolio.maxCountReached");
        verify(portfolioRepository, never()).save(any());
        verify(portfolioRepository, never()).findByUserSubAndName(any(), any());
    }

    @Test
    void shouldPersistNewPositionWithRequestFields_whenPortfolioOwnedByUser() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        LocalDateTime entryDate = LocalDateTime.of(2024, 1, 15, 10, 0);
        PositionRequest request = new PositionRequest(
                "stock", "THYAO.IS", new BigDecimal("100"), entryDate, new BigDecimal("40"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTrackedAsset(TrackedAssetType.STOCK, "THYAO.IS");

        service.addPosition(PORTFOLIO_ID, USER_SUB, request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository).save(captor.capture());
        PortfolioPosition saved = captor.getValue();
        assertThat(saved.getAssetType()).isEqualTo(AssetType.STOCK);
        assertThat(saved.getAssetCode()).isEqualTo("THYAO.IS");
        assertThat(saved.getQuantity()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(saved.getEntryDate()).isEqualTo(entryDate);
        assertThat(saved.getEntryPrice()).isEqualByComparingTo(new BigDecimal("40"));
    }

    @Test
    void shouldPersistClosedPosition_whenExitFieldsProvidedOnAdd() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        LocalDateTime entryDate = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime exitDate = LocalDateTime.of(2024, 2, 20, 16, 30);
        PositionRequest request = new PositionRequest(
                "stock", "THYAO.IS", new BigDecimal("100"), entryDate, new BigDecimal("40"),
                exitDate, new BigDecimal("55"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTrackedAsset(TrackedAssetType.STOCK, "THYAO.IS");

        service.addPosition(PORTFOLIO_ID, USER_SUB, request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository).save(captor.capture());
        PortfolioPosition saved = captor.getValue();
        assertThat(saved.isClosed()).isTrue();
        assertThat(saved.getExitDate()).isEqualTo(exitDate);
        assertThat(saved.getExitPrice()).isEqualByComparingTo(new BigDecimal("55"));
    }

    @Test
    void shouldThrowResourceNotFound_whenAddingPositionToPortfolioNotOwned() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("100"), LocalDateTime.now(), new BigDecimal("40"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(positionRepository, never()).save(any());
    }

    @Test
    void shouldDelegateLotUpdateToDomainAndPersist_whenPositionOwned() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"));
        LocalDateTime newDate = LocalDateTime.of(2025, 5, 1, 9, 0);
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("150"), newDate, new BigDecimal("55"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updatePosition(PORTFOLIO_ID, 33L, USER_SUB, request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository).save(captor.capture());
        PortfolioPosition updated = captor.getValue();
        assertThat(updated.getQuantity()).isEqualByComparingTo(new BigDecimal("150"));
        assertThat(updated.getEntryPrice()).isEqualByComparingTo(new BigDecimal("55"));
        assertThat(updated.getEntryDate()).isEqualTo(newDate);
    }

    @Test
    void shouldThrowBusinessException_whenUpdatingPositionFromAnotherPortfolio() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition foreign = stubPosition(999L, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("100"), new BigDecimal("40"));
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("150"), LocalDateTime.now(), new BigDecimal("55"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.updatePosition(PORTFOLIO_ID, 33L, USER_SUB, request))
                .isInstanceOf(BusinessException.class);
        verify(positionRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFound_whenDeletingMissingPosition() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePosition(PORTFOLIO_ID, 404L, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(positionRepository, never()).delete(any());
    }

    @Test
    void shouldDeletePositionAndCleanSnapshots_whenLastLotForAsset() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.5"), new BigDecimal("2400000"));
        existing.setTrackedAsset(TrackedAsset.builder().id(7L)
                .assetType(TrackedAssetType.CRYPTO).assetCode("bitcoin").build());
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.existsByPortfolioIdAndTrackedAsset_IdAndIdNot(PORTFOLIO_ID, 7L, 33L))
                .thenReturn(false);

        service.deletePosition(PORTFOLIO_ID, 33L, USER_SUB);

        verify(positionRepository).delete(existing);
        verify(assetSnapshotRepository).deleteByPortfolioIdAndAssetTypeAndAssetCode(
                PORTFOLIO_ID, AssetType.CRYPTO, "bitcoin");
    }

    @Test
    void shouldDeletePositionButKeepSnapshots_whenOtherLotExistsForSameAsset() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.5"), new BigDecimal("2400000"));
        existing.setTrackedAsset(TrackedAsset.builder().id(7L)
                .assetType(TrackedAssetType.CRYPTO).assetCode("bitcoin").build());
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.existsByPortfolioIdAndTrackedAsset_IdAndIdNot(PORTFOLIO_ID, 7L, 33L))
                .thenReturn(true);

        service.deletePosition(PORTFOLIO_ID, 33L, USER_SUB);

        verify(positionRepository).delete(existing);
        verify(assetSnapshotRepository, never()).deleteByPortfolioIdAndAssetTypeAndAssetCode(
                any(), any(), any());
    }

    @Test
    void should_throwBusinessException_when_entryDateBeforeMinAllowed() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("10"),
                LocalDateTime.of(1900, 1, 1, 9, 0), new BigDecimal("40"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.entryDateTooOld");
        verify(positionRepository, never()).save(any());
    }

    @Test
    void should_throwBusinessException_when_entryDateIsInFuture() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("10"),
                LocalDateTime.now().plusDays(5), new BigDecimal("40"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.entryDateInFuture");
        verify(positionRepository, never()).save(any());
    }

    @Test
    void should_throwBusinessException_when_entryPriceBelowMinAllowed() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("10"),
                LocalDateTime.of(2024, 1, 1, 9, 0), new BigDecimal("0.00000001"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.priceTooLow");
    }

    @Test
    void should_throwBusinessException_when_entryPriceAboveMaxAllowed() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("10"),
                LocalDateTime.of(2024, 1, 1, 9, 0), new BigDecimal("9999999999"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.priceTooHigh");
    }

    @Test
    void should_throwBusinessException_when_quantityBelowMinAllowed() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("0.000000001"),
                LocalDateTime.of(2024, 1, 1, 9, 0), new BigDecimal("40"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.quantityTooLow");
    }

    @Test
    void should_throwBusinessException_when_quantityAboveMaxAllowed() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("9999999999"),
                LocalDateTime.of(2024, 1, 1, 9, 0), new BigDecimal("40"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.lot.quantityTooHigh");
    }

    @Test
    void should_throwBusinessException_when_addingPositionForUnknownTrackedAsset() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(trackedAssetRepository.findByAssetTypeAndAssetCodeIgnoreCase(TrackedAssetType.STOCK, "GHOST.IS"))
                .thenReturn(Optional.empty());
        PositionRequest request = new PositionRequest(
                "STOCK", "GHOST.IS", new BigDecimal("10"),
                LocalDateTime.of(2024, 1, 1, 9, 0), new BigDecimal("40"));

        assertThatThrownBy(() -> service.addPosition(PORTFOLIO_ID, USER_SUB, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.assetNotTracked");
        verify(positionRepository, never()).save(any());
    }

    @Test
    void should_publishLotChangedEvent_when_addingPosition() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        LocalDateTime entryDate = LocalDateTime.of(2024, 1, 15, 10, 0);
        PositionRequest request = new PositionRequest(
                "stock", "THYAO.IS", new BigDecimal("100"), entryDate, new BigDecimal("40"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTrackedAsset(TrackedAssetType.STOCK, "THYAO.IS");

        service.addPosition(PORTFOLIO_ID, USER_SUB, request);

        verify(eventPublisher).publishEvent(any(PortfolioBackfillService.LotChangedEvent.class));
    }

    @Test
    void should_publishLotChangedEvent_when_deletingPosition() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("40"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));

        service.deletePosition(PORTFOLIO_ID, 33L, USER_SUB);

        verify(eventPublisher).publishEvent(any(PortfolioBackfillService.LotChangedEvent.class));
    }

    @Test
    void should_useEarliestEntryDate_when_updateMovesEntryEarlier() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = PortfolioPosition.builder()
                .portfolioId(PORTFOLIO_ID)
                .assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .quantity(new BigDecimal("100"))
                .entryDate(LocalDateTime.of(2024, 6, 1, 10, 0))
                .entryPrice(new BigDecimal("40"))
                .build();
        LocalDateTime earlier = LocalDateTime.of(2024, 1, 1, 9, 0);
        PositionRequest request = new PositionRequest(
                "STOCK", "THYAO.IS", new BigDecimal("100"), earlier, new BigDecimal("40"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updatePosition(PORTFOLIO_ID, 33L, USER_SUB, request);

        ArgumentCaptor<PortfolioBackfillService.LotChangedEvent> captor =
                ArgumentCaptor.forClass(PortfolioBackfillService.LotChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().fromDate()).isEqualTo(earlier.toLocalDate());
    }

    @Test
    void addPosition_convertsEntryPriceToTry_whenPriceCurrencyIsUsd() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        LocalDateTime entryDate = LocalDateTime.of(2024, 1, 15, 10, 0);
        PositionRequest request = new PositionRequest(
                "CRYPTO", "bitcoin", new BigDecimal("0.5"), entryDate,
                new BigDecimal("61299"), null, null, "USD");
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        stubTrackedAsset(TrackedAssetType.CRYPTO, "bitcoin");
        when(currencyConverter.convertAtDate(new BigDecimal("61299"),
                com.finance.common.model.Currency.USD, com.finance.common.model.Currency.TRY,
                entryDate.toLocalDate())).thenReturn(new BigDecimal("2023867.0000"));

        service.addPosition(PORTFOLIO_ID, USER_SUB, request);

        ArgumentCaptor<PortfolioPosition> captor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryPrice()).isEqualByComparingTo(new BigDecimal("2023867.0000"));
    }

    @Test
    void sellPosition_convertsExitPriceToTry_whenPriceCurrencyIsUsd() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.5"), new BigDecimal("2023867"));
        existing.setTrackedAsset(TrackedAsset.builder().id(1L)
                .assetType(TrackedAssetType.CRYPTO).assetCode("bitcoin").build());
        existing.updateLot(LocalDateTime.of(2024, 1, 1, 9, 0), null, null);
        LocalDateTime exitDate = LocalDateTime.of(2024, 6, 1, 12, 0);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(currencyConverter.convertAtDate(new BigDecimal("70000"),
                com.finance.common.model.Currency.USD, com.finance.common.model.Currency.TRY,
                exitDate.toLocalDate())).thenReturn(new BigDecimal("2310000.0000"));
        PositionSellRequest req = new PositionSellRequest(
                new BigDecimal("0.5"), new BigDecimal("70000"), exitDate, "USD");

        service.sellPosition(PORTFOLIO_ID, 33L, USER_SUB, req);

        assertThat(existing.getExitPrice()).isEqualByComparingTo(new BigDecimal("2310000.0000"));
    }

    private PortfolioPosition stubPosition(Long portfolioId, AssetType type, String code,
                                            BigDecimal qty, BigDecimal entryPrice) {
        return PortfolioPosition.builder()
                .portfolioId(portfolioId)
                .assetType(type).assetCode(code)
                .quantity(qty)
                .entryDate(LocalDateTime.now())
                .entryPrice(entryPrice)
                .build();
    }

    @Test
    void should_closeWholePosition_when_sellQuantityEqualsHolding() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("10"), new BigDecimal("40"));
        existing.setTrackedAsset(TrackedAsset.builder().id(1L).assetType(TrackedAssetType.STOCK).assetCode("THYAO.IS").build());
        existing.updateLot(LocalDateTime.of(2024, 1, 1, 9, 0), null, null);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        PositionSellRequest req = new PositionSellRequest(
                new BigDecimal("10"), new BigDecimal("55"), LocalDateTime.of(2024, 6, 1, 12, 0));

        service.sellPosition(PORTFOLIO_ID, 33L, USER_SUB, req);

        assertThat(existing.isClosed()).isTrue();
        assertThat(existing.getExitPrice()).isEqualByComparingTo("55");
        verify(positionRepository, never()).save(argThat(p -> p != existing));
        verify(eventPublisher).publishEvent(any(PortfolioBackfillService.LotChangedEvent.class));
    }

    @Test
    void should_splitPosition_when_sellQuantityIsLessThanHolding() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        TrackedAsset tracked = TrackedAsset.builder().id(1L).assetType(TrackedAssetType.STOCK).assetCode("THYAO.IS").build();
        PortfolioPosition existing = PortfolioPosition.builder()
                .portfolio(portfolio)
                .portfolioId(PORTFOLIO_ID)
                .assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .quantity(new BigDecimal("10"))
                .entryDate(LocalDateTime.of(2024, 1, 1, 9, 0))
                .entryPrice(new BigDecimal("40"))
                .build();
        existing.setTrackedAsset(tracked);
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));
        PositionSellRequest req = new PositionSellRequest(
                new BigDecimal("4"), new BigDecimal("55"), LocalDateTime.of(2024, 6, 1, 12, 0));

        service.sellPosition(PORTFOLIO_ID, 33L, USER_SUB, req);

        assertThat(existing.isClosed()).isFalse();
        assertThat(existing.getQuantity()).isEqualByComparingTo("6");
        ArgumentCaptor<PortfolioPosition> cap = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(positionRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        PortfolioPosition closedSlice = cap.getAllValues().stream()
                .filter(PortfolioPosition::isClosed).findFirst().orElseThrow();
        assertThat(closedSlice.getQuantity()).isEqualByComparingTo("4");
        assertThat(closedSlice.getEntryPrice()).isEqualByComparingTo("40");
        assertThat(closedSlice.getExitPrice()).isEqualByComparingTo("55");
    }

    @Test
    void should_rejectSell_when_quantityExceedsHolding() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("5"), new BigDecimal("40"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        PositionSellRequest req = new PositionSellRequest(
                new BigDecimal("10"), new BigDecimal("55"), LocalDateTime.now());

        assertThatThrownBy(() -> service.sellPosition(PORTFOLIO_ID, 33L, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.sell.quantityExceedsPosition");
    }

    @Test
    void should_rejectSell_when_exitDateIsBeforeEntry() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = PortfolioPosition.builder()
                .portfolioId(PORTFOLIO_ID).assetType(AssetType.STOCK).assetCode("THYAO.IS")
                .quantity(new BigDecimal("5")).entryPrice(new BigDecimal("40"))
                .entryDate(LocalDateTime.of(2025, 6, 1, 9, 0))
                .build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        PositionSellRequest req = new PositionSellRequest(
                new BigDecimal("5"), new BigDecimal("55"), LocalDateTime.of(2024, 12, 1, 9, 0));

        assertThatThrownBy(() -> service.sellPosition(PORTFOLIO_ID, 33L, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.portfolio.sell.dateBeforeEntry");
    }

    @Test
    void should_rejectSell_when_assetTypeIsViop() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.VIOP, "F_USDTRY",
                new BigDecimal("1"), new BigDecimal("33"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        PositionSellRequest req = new PositionSellRequest(
                new BigDecimal("1"), new BigDecimal("34"), LocalDateTime.now());

        assertThatThrownBy(() -> service.sellPosition(PORTFOLIO_ID, 33L, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("useDerivativeClose");
    }

    @Test
    void should_rejectSell_when_positionAlreadyClosed() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("5"), new BigDecimal("40"));
        existing.closeWith(LocalDateTime.now(), new BigDecimal("50"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        PositionSellRequest req = new PositionSellRequest(
                new BigDecimal("5"), new BigDecimal("55"), LocalDateTime.now());

        assertThatThrownBy(() -> service.sellPosition(PORTFOLIO_ID, 33L, USER_SUB, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("alreadyClosed");
    }

    @Test
    void should_reopenClosedPosition_when_clearingExitFields() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("5"), new BigDecimal("40"));
        existing.closeWith(LocalDateTime.now(), new BigDecimal("50"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));
        when(positionRepository.save(any(PortfolioPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reopenPosition(PORTFOLIO_ID, 33L, USER_SUB);

        assertThat(existing.isClosed()).isFalse();
        assertThat(existing.getExitDate()).isNull();
        assertThat(existing.getExitPrice()).isNull();
        verify(eventPublisher).publishEvent(any(PortfolioBackfillService.LotChangedEvent.class));
    }

    @Test
    void should_rejectReopen_when_positionIsOpen() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.STOCK, "THYAO.IS",
                new BigDecimal("5"), new BigDecimal("40"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.reopenPosition(PORTFOLIO_ID, 33L, USER_SUB))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("notClosed");
    }

    @Test
    void should_updatePortfolioName_when_renamingToUniqueName() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).name("Old").build();
        PortfolioCreateRequest request = new PortfolioCreateRequest("New Name");
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.findByUserSubAndName(USER_SUB, "New Name")).thenReturn(Optional.empty());
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toPortfolioResponse(any(Portfolio.class)))
                .thenReturn(new PortfolioResponse(PORTFOLIO_ID, "New Name", null));

        PortfolioResponse result = service.renamePortfolio(USER_SUB, PORTFOLIO_ID, request);

        assertThat(portfolio.getName()).isEqualTo("New Name");
        assertThat(result.name()).isEqualTo("New Name");
        verify(portfolioRepository).save(portfolio);
    }

    @Test
    void should_rejectRename_when_anotherPortfolioOwnsTargetName() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).name("Old").build();
        Portfolio sibling = Portfolio.builder().id(99L).userSub(USER_SUB).name("Taken").build();
        PortfolioCreateRequest request = new PortfolioCreateRequest("Taken");
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.findByUserSubAndName(USER_SUB, "Taken")).thenReturn(Optional.of(sibling));

        assertThatThrownBy(() -> service.renamePortfolio(USER_SUB, PORTFOLIO_ID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicateName");
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void should_allowRenameToSameName_when_onlyOwningPortfolioHasIt() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).name("Main").build();
        PortfolioCreateRequest request = new PortfolioCreateRequest("Main");
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.findByUserSubAndName(USER_SUB, "Main")).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toPortfolioResponse(any(Portfolio.class)))
                .thenReturn(new PortfolioResponse(PORTFOLIO_ID, "Main", null));

        PortfolioResponse result = service.renamePortfolio(USER_SUB, PORTFOLIO_ID, request);

        assertThat(result.name()).isEqualTo("Main");
        verify(portfolioRepository).save(portfolio);
    }

    @Test
    void should_rejectRename_when_portfolioNotFound() {
        PortfolioCreateRequest request = new PortfolioCreateRequest("Anything");
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renamePortfolio(USER_SUB, PORTFOLIO_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void should_cascadeDeleteRelatedRows_when_deletingPortfolio() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).name("Doomed").build();
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));

        service.deletePortfolio(USER_SUB, PORTFOLIO_ID);

        verify(derivativePositionRepository).deleteByPortfolio_Id(PORTFOLIO_ID);
        verify(positionRepository).deleteByPortfolioId(PORTFOLIO_ID);
        verify(assetSnapshotRepository).deleteByPortfolioId(PORTFOLIO_ID);
        verify(dailySnapshotRepository).deleteByPortfolioId(PORTFOLIO_ID);
        verify(portfolioRepository).delete(portfolio);
    }

    @Test
    void should_rejectDelete_when_portfolioNotFound() {
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePortfolio(USER_SUB, PORTFOLIO_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(positionRepository, never()).deleteByPortfolioId(any());
        verify(portfolioRepository, never()).delete(any());
    }
}
