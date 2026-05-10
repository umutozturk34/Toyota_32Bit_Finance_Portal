package com.finance.portfolio.service;
import com.finance.market.core.service.MarketSnapshotProcessor;


import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioCrudServiceTest {

    private static final String USER_SUB = "user-1";
    private static final Long PORTFOLIO_ID = 7L;

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private PortfolioPositionRepository positionRepository;
    @Mock private TrackedAssetRepository trackedAssetRepository;
    @Mock private PortfolioResponseMapper mapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final PortfolioProperties portfolioProperties = new PortfolioProperties();
    private PortfolioCrudService service;

    @BeforeEach
    void setUp() {
        service = new PortfolioCrudService(portfolioRepository, positionRepository, trackedAssetRepository,
                mapper, eventPublisher, portfolioProperties);
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
    void shouldDeletePosition_whenOwnershipValid() {
        Portfolio portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).build();
        PortfolioPosition existing = stubPosition(PORTFOLIO_ID, AssetType.CRYPTO, "bitcoin",
                new BigDecimal("0.5"), new BigDecimal("2400000"));
        when(portfolioRepository.findByIdAndUserSub(PORTFOLIO_ID, USER_SUB)).thenReturn(Optional.of(portfolio));
        when(positionRepository.findById(33L)).thenReturn(Optional.of(existing));

        service.deletePosition(PORTFOLIO_ID, 33L, USER_SUB);

        verify(positionRepository).delete(existing);
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
}
