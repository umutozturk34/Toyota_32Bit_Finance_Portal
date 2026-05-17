package com.finance.portfolio.derivative.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.core.service.HistoricalPricingPort;
import com.finance.market.viop.model.ViopCandle;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.model.ViopContractKind;
import com.finance.market.viop.port.ViopMarketDataPort;
import com.finance.market.viop.repository.ViopCandleRepository;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.market.viop.service.ViopHistoryProvider;
import com.finance.portfolio.derivative.dto.request.CloseDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.OpenDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.UpdateDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.mapper.DerivativePositionMapper;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.portfolio.service.SnapshotCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DerivativePositionServiceTest {

    private static final String USER_SUB = "user-1";
    private static final Long PORTFOLIO_ID = 1L;
    private static final Long POSITION_ID = 100L;

    @Mock private DerivativePositionRepository positionRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ViopContractRepository contractRepository;
    @Mock private ViopCandleRepository candleRepository;
    @Mock private ViopHistoryProvider historyProvider;
    @Mock private HistoricalPricingPort historicalPricingPort;
    @Mock private PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    @Mock private SnapshotCalculationService snapshotCalculator;
    @Mock private ViopMarketDataPort viopMarketData;
    @Mock private DerivativePositionMapper mapper;

    private DerivativePositionService service;
    private Portfolio portfolio;
    private ViopContract contract;

    @BeforeEach
    void setUp() {
        service = new DerivativePositionService(positionRepository, portfolioRepository,
                contractRepository, candleRepository, historyProvider, historicalPricingPort,
                assetSnapshotRepository, snapshotCalculator, viopMarketData, mapper);

        portfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub(USER_SUB).name("test").build();

        contract = ViopContract.builder()
                .symbol("F_USDTRY0626")
                .kind(ViopContractKind.FUTURE)
                .contractSize(new BigDecimal("1000"))
                .currency("TRY")
                .active(true)
                .lastPrice(new BigDecimal("35.50"))
                .expiryDate(LocalDate.of(2026, 6, 30))
                .build();

        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(portfolio));
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                any(), any(), any())).thenReturn(List.<ViopCandle>of());
        lenient().when(mapper.toResponse(any())).thenReturn(null);
    }

    private DerivativePosition openPosition() {
        return DerivativePosition.builder()
                .id(POSITION_ID)
                .portfolio(portfolio)
                .viopContract(contract)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("35.20"))
                .quantityLot(new BigDecimal("1"))
                .build();
    }

    private DerivativePosition closedPosition() {
        DerivativePosition pos = openPosition();
        pos.closeWith(LocalDate.of(2026, 5, 1), new BigDecimal("36.00"), DerivativeCloseReason.USER_CLOSED);
        return pos;
    }

    @Test
    void should_throwResourceNotFoundException_when_positionIdDoesNotExist() {
        UpdateDerivativePositionRequest req = new UpdateDerivativePositionRequest(
                DerivativeDirection.LONG, LocalDate.of(2026, 4, 1), new BigDecimal("35.20"), new BigDecimal("1"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateOpen(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.derivative.positionNotFound");
    }

    @Test
    void should_throwBadRequest_when_updatingClosedPosition() {
        UpdateDerivativePositionRequest req = new UpdateDerivativePositionRequest(
                DerivativeDirection.LONG, LocalDate.of(2026, 4, 1), new BigDecimal("35.20"), new BigDecimal("1"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(closedPosition()));

        assertThatThrownBy(() -> service.updateOpen(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.derivative.alreadyClosed");
    }

    @Test
    void should_throwBadRequest_when_entryDateIsAfterContractExpiry() {
        UpdateDerivativePositionRequest req = new UpdateDerivativePositionRequest(
                DerivativeDirection.SHORT, LocalDate.of(2027, 1, 1), new BigDecimal("35.20"), new BigDecimal("1"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(openPosition()));

        assertThatThrownBy(() -> service.updateOpen(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.viop.entryAfterExpiry");
    }

    @Test
    void should_applyEntryUpdateAndRebuildSnapshots_when_requestIsValid() {
        UpdateDerivativePositionRequest req = new UpdateDerivativePositionRequest(
                DerivativeDirection.SHORT, LocalDate.of(2026, 4, 15),
                new BigDecimal("36.00"), new BigDecimal("3"));
        DerivativePosition position = openPosition();
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.of(position));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(position));

        service.updateOpen(POSITION_ID, PORTFOLIO_ID, USER_SUB, req);

        assertThat(position.getDirection()).isEqualTo(DerivativeDirection.SHORT);
        assertThat(position.getEntryDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(position.getEntryPrice()).isEqualByComparingTo("36.00");
        assertThat(position.getQuantityLot()).isEqualByComparingTo("3");
        verify(assetSnapshotRepository).deleteByPortfolioIdAndAssetTypeAndAssetCode(
                eq(PORTFOLIO_ID), any(), eq("F_USDTRY0626"));
    }

    @Test
    void should_clearCloseFieldsAndRebuildSnapshots_when_reopeningClosedPosition() {
        DerivativePosition position = closedPosition();
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.of(position));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(position));

        service.reopen(POSITION_ID, PORTFOLIO_ID, USER_SUB);

        assertThat(position.isOpen()).isTrue();
        assertThat(position.getClosePrice()).isNull();
        assertThat(position.getCloseReason()).isNull();
        verify(assetSnapshotRepository).deleteByPortfolioIdAndAssetTypeAndAssetCode(
                eq(PORTFOLIO_ID), any(), eq("F_USDTRY0626"));
    }

    @Test
    void should_throwBadRequest_when_reopeningOpenPosition() {
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(openPosition()));

        assertThatThrownBy(() -> service.reopen(POSITION_ID, PORTFOLIO_ID, USER_SUB))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.derivative.notClosed");
    }

    @Test
    void should_throwBadRequest_when_closeDateIsBeforeEntryDate() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 3, 1), new BigDecimal("36.00"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(openPosition()));

        assertThatThrownBy(() -> service.close(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.derivative.closeBeforeEntry");
    }

    @Test
    void should_throwBadRequest_when_closingAlreadyClosedPosition() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 1), new BigDecimal("36.00"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(closedPosition()));

        assertThatThrownBy(() -> service.close(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.derivative.alreadyClosed");
    }

    @Test
    void should_partialCloseAndKeepRemainderOpen_when_closeQtyIsLessThanPosition() {
        DerivativePosition position = DerivativePosition.builder()
                .id(POSITION_ID).portfolio(portfolio).viopContract(contract)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("35.20"))
                .quantityLot(new BigDecimal("5"))
                .build();
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 1), new BigDecimal("36.00"), new BigDecimal("2"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(position));
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(positionRepository.findOpenByPortfolio(PORTFOLIO_ID)).thenReturn(List.of());

        service.close(POSITION_ID, PORTFOLIO_ID, USER_SUB, req);

        assertThat(position.isOpen()).isTrue();
        assertThat(position.getQuantityLot()).isEqualByComparingTo("3");
        org.mockito.ArgumentCaptor<DerivativePosition> cap = org.mockito.ArgumentCaptor.forClass(DerivativePosition.class);
        verify(positionRepository, org.mockito.Mockito.atLeast(2)).save(cap.capture());
        DerivativePosition closedSlice = cap.getAllValues().stream()
                .filter(p -> !p.isOpen()).findFirst().orElseThrow();
        assertThat(closedSlice.getQuantityLot()).isEqualByComparingTo("2");
        assertThat(closedSlice.getEntryPrice()).isEqualByComparingTo("35.20");
        assertThat(closedSlice.getClosePrice()).isEqualByComparingTo("36.00");
    }

    @Test
    void should_throwBadRequest_when_closeQuantityExceedsPosition() {
        DerivativePosition position = openPosition();
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 1), new BigDecimal("36.00"), new BigDecimal("99"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(position));

        assertThatThrownBy(() -> service.close(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("closeQtyExceedsPosition");
    }

    @Test
    void should_throwBadRequest_when_updateCloseTargetIsOpen() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 1), new BigDecimal("36.00"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(openPosition()));

        assertThatThrownBy(() -> service.updateClose(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.derivative.notClosed");
    }

    @Test
    void should_deletePositionAndRebuildPeers_when_deletingExistingPosition() {
        DerivativePosition position = openPosition();
        DerivativePosition peer = DerivativePosition.builder()
                .id(200L)
                .portfolio(portfolio)
                .viopContract(contract)
                .direction(DerivativeDirection.SHORT)
                .entryDate(LocalDate.of(2026, 4, 5))
                .entryPrice(new BigDecimal("35.40"))
                .quantityLot(new BigDecimal("2"))
                .build();
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.of(position));
        when(positionRepository.findOpenByPortfolio(PORTFOLIO_ID)).thenReturn(List.of(peer));

        service.delete(POSITION_ID, PORTFOLIO_ID, USER_SUB);

        verify(positionRepository).delete(position);
        verify(assetSnapshotRepository).deleteByPortfolioIdAndAssetTypeAndAssetCode(
                eq(PORTFOLIO_ID), any(), eq("F_USDTRY0626"));
    }

    @Test
    void should_throwResourceNotFound_when_deletingMissingPosition() {
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(POSITION_ID, PORTFOLIO_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(positionRepository, never()).delete(any(DerivativePosition.class));
    }

    @Test
    void should_throwResourceNotFound_when_openingWithUnknownContract() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_MISSING", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.viop.contractNotFound");
    }

    @Test
    void should_throwBadRequest_when_openingInactiveContract() {
        contract.setActive(false);
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.open(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.viop.contractInactive");
    }

    @Test
    void should_throwBadRequest_when_openingContractWithoutLastPrice() {
        contract.setLastPrice(null);
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.open(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.viop.contractNotTradeable");
    }

    @Test
    void should_throwBadRequest_when_openEntryAfterContractExpiry() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2027, 1, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.open(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.viop.entryAfterExpiry");
    }

    @Test
    void should_persistOpenPositionAndBackfillSnapshots_when_requestIsValid() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.open(PORTFOLIO_ID, USER_SUB, req);

        verify(positionRepository).save(any(DerivativePosition.class));
        verify(historyProvider).fetchAndPersist(eq("F_USDTRY0626"), any(), any());
    }

    @Test
    void should_listAllPositions_when_listInvoked() {
        DerivativePosition pos = openPosition();
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(pos));
        when(mapper.toResponses(any())).thenReturn(List.of());

        service.list(PORTFOLIO_ID, USER_SUB);

        verify(positionRepository).findByPortfolioId(PORTFOLIO_ID);
    }

    @Test
    void should_listOnlyOpenPositions_when_listOpenInvoked() {
        when(positionRepository.findOpenByPortfolio(PORTFOLIO_ID)).thenReturn(List.of(openPosition()));
        when(mapper.toResponses(any())).thenReturn(List.of());

        service.listOpen(PORTFOLIO_ID, USER_SUB);

        verify(positionRepository).findOpenByPortfolio(PORTFOLIO_ID);
    }

    @Test
    void should_returnZero_when_autoCloseExpiredFindsNothing() {
        when(positionRepository.findOpenWithExpiredContract(any())).thenReturn(List.of());

        int closed = service.autoCloseExpired();

        assertThat(closed).isZero();
    }

    @Test
    void should_closeExpiredPositionsWithSettlementPrice_when_autoCloseExpiredRuns() {
        contract.setSettlementPrice(new BigDecimal("36.00"));
        contract.setExpiryDate(LocalDate.of(2026, 6, 30));
        DerivativePosition pos = openPosition();
        when(positionRepository.findOpenWithExpiredContract(any())).thenReturn(List.of(pos));
        when(positionRepository.findOpenByPortfolio(PORTFOLIO_ID)).thenReturn(List.of());

        int closed = service.autoCloseExpired();

        assertThat(closed).isEqualTo(1);
        assertThat(pos.isOpen()).isFalse();
        assertThat(pos.getClosePrice()).isEqualByComparingTo("36.00");
    }

    @Test
    void should_skipPosition_when_expiredContractHasNoSettlementOrLastPrice() {
        contract.setSettlementPrice(null);
        contract.setLastPrice(null);
        DerivativePosition pos = openPosition();
        when(positionRepository.findOpenWithExpiredContract(any())).thenReturn(List.of(pos));

        int closed = service.autoCloseExpired();

        assertThat(closed).isZero();
        assertThat(pos.isOpen()).isTrue();
    }

    @Test
    void should_fallBackToLastPrice_when_expiredContractMissingSettlement() {
        contract.setSettlementPrice(null);
        contract.setLastPrice(new BigDecimal("36.50"));
        DerivativePosition pos = openPosition();
        when(positionRepository.findOpenWithExpiredContract(any())).thenReturn(List.of(pos));
        when(positionRepository.findOpenByPortfolio(PORTFOLIO_ID)).thenReturn(List.of());

        service.autoCloseExpired();

        assertThat(pos.getClosePrice()).isEqualByComparingTo("36.50");
    }

    @Test
    void should_throwResourceNotFoundException_when_portfolioIsNotOwnedByUser() {
        Portfolio otherPortfolio = Portfolio.builder().id(PORTFOLIO_ID).userSub("other-user").name("test").build();
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(otherPortfolio));

        assertThatThrownBy(() -> service.list(PORTFOLIO_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.portfolio.notFound");
    }

    @Test
    void should_throwResourceNotFoundException_when_portfolioDoesNotExist() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(PORTFOLIO_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_resolveEntryPriceFromHistory_when_openRequestMissesEntryPrice() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                null, new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(viopMarketData.fetchHistory(eq("F_USDTRY0626"), any(), any(), any())).thenReturn(List.of(
                new com.finance.market.viop.dto.ViopHistoryPoint(
                        java.time.LocalDateTime.of(2026, 4, 1, 18, 0),
                        new BigDecimal("35.40"))));
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.open(PORTFOLIO_ID, USER_SUB, req);

        verify(positionRepository).save(any(DerivativePosition.class));
    }

    @Test
    void should_throwBadRequest_when_entryPriceCannotBeResolvedFromHistory() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                null, new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(viopMarketData.fetchHistory(eq("F_USDTRY0626"), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.open(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.viop.entryPriceUnavailable");
    }

    @Test
    void should_openAndImmediatelyClose_when_openRequestIncludesCloseDate() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"),
                LocalDate.of(2026, 4, 20), new BigDecimal("36.00"));
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(positionRepository.save(any())).thenAnswer(inv -> {
            DerivativePosition saved = inv.getArgument(0);
            return saved;
        });

        service.open(PORTFOLIO_ID, USER_SUB, req);

        verify(positionRepository).save(any(DerivativePosition.class));
    }

    @Test
    void should_throwBadRequest_when_openCloseDateProvidedButHistoryEmpty() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"),
                LocalDate.of(2026, 4, 20), null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(viopMarketData.fetchHistory(eq("F_USDTRY0626"), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.open(PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.viop.closePriceUnavailable");
    }

    @Test
    void should_throwResourceNotFoundException_when_closingMissingPosition() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 1), new BigDecimal("36.00"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("error.derivative.positionNotFound");
    }

    @Test
    void should_throwBadRequest_when_closeDateBeforeEntryDateOnUpdateClose() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 3, 1), new BigDecimal("36.00"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID))
                .thenReturn(Optional.of(closedPosition()));

        assertThatThrownBy(() -> service.updateClose(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("error.derivative.closeBeforeEntry");
    }

    @Test
    void should_updateClosePriceAndRebuildSnapshots_when_updateCloseValid() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 10), new BigDecimal("37.50"));
        DerivativePosition position = closedPosition();
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.of(position));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(position));

        service.updateClose(POSITION_ID, PORTFOLIO_ID, USER_SUB, req);

        assertThat(position.isOpen()).isFalse();
        assertThat(position.getClosePrice()).isEqualByComparingTo("37.50");
        assertThat(position.getCloseDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        verify(assetSnapshotRepository).deleteByPortfolioIdAndAssetTypeAndAssetCode(
                eq(PORTFOLIO_ID), any(), eq("F_USDTRY0626"));
    }

    @Test
    void should_throwResourceNotFound_when_updateClosePositionMissing() {
        CloseDerivativePositionRequest req = new CloseDerivativePositionRequest(
                LocalDate.of(2026, 5, 1), new BigDecimal("36.00"));
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateClose(POSITION_ID, PORTFOLIO_ID, USER_SUB, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_throwResourceNotFound_when_reopenPositionMissing() {
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopen(POSITION_ID, PORTFOLIO_ID, USER_SUB))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_rebuildPeerSnapshots_when_updatingOpenPositionHasPeers() {
        UpdateDerivativePositionRequest req = new UpdateDerivativePositionRequest(
                DerivativeDirection.LONG, LocalDate.of(2026, 4, 15),
                new BigDecimal("36.00"), new BigDecimal("3"));
        DerivativePosition position = openPosition();
        DerivativePosition peer = DerivativePosition.builder()
                .id(200L)
                .portfolio(portfolio)
                .viopContract(contract)
                .direction(DerivativeDirection.SHORT)
                .entryDate(LocalDate.of(2026, 4, 5))
                .entryPrice(new BigDecimal("35.40"))
                .quantityLot(new BigDecimal("2"))
                .build();
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.of(position));
        when(positionRepository.findByPortfolioId(PORTFOLIO_ID)).thenReturn(List.of(position, peer));

        service.updateOpen(POSITION_ID, PORTFOLIO_ID, USER_SUB, req);

        verify(historyProvider, org.mockito.Mockito.atLeastOnce())
                .fetchAndPersist(eq("F_USDTRY0626"), any(), any());
    }

    @Test
    void should_skipDelete_when_positionHasNullContract() {
        DerivativePosition position = DerivativePosition.builder()
                .id(POSITION_ID)
                .portfolio(portfolio)
                .viopContract(null)
                .direction(DerivativeDirection.LONG)
                .entryDate(LocalDate.of(2026, 4, 1))
                .entryPrice(new BigDecimal("35.20"))
                .quantityLot(new BigDecimal("1"))
                .build();
        when(positionRepository.findByIdAndPortfolioId(POSITION_ID, PORTFOLIO_ID)).thenReturn(Optional.of(position));

        service.delete(POSITION_ID, PORTFOLIO_ID, USER_SUB);

        verify(positionRepository).delete(position);
        verify(assetSnapshotRepository, never())
                .deleteByPortfolioIdAndAssetTypeAndAssetCode(anyLong(), any(), any());
    }

    @Test
    void should_handleForeignCurrencyAutoClose_when_contractCurrencyIsUsd() {
        contract.setCurrency("USD");
        contract.setSettlementPrice(new BigDecimal("3.5"));
        contract.setExpiryDate(LocalDate.of(2026, 6, 30));
        DerivativePosition pos = openPosition();
        when(positionRepository.findOpenWithExpiredContract(any())).thenReturn(List.of(pos));
        when(positionRepository.findOpenByPortfolio(PORTFOLIO_ID)).thenReturn(List.of());
        when(historicalPricingPort.getPriceSeries(any(), eq("USD"), any(), any()))
                .thenReturn(java.util.Map.of(LocalDate.of(2026, 6, 30), new BigDecimal("32.0")));

        int closed = service.autoCloseExpired();

        assertThat(closed).isEqualTo(1);
        assertThat(pos.getClosePrice()).isEqualByComparingTo("112.0");
    }

    @Test
    void should_fallbackToNativePriceOnAutoClose_when_noFxSeriesAvailable() {
        contract.setCurrency("USD");
        contract.setSettlementPrice(new BigDecimal("3.5"));
        contract.setExpiryDate(LocalDate.of(2026, 6, 30));
        DerivativePosition pos = openPosition();
        when(positionRepository.findOpenWithExpiredContract(any())).thenReturn(List.of(pos));
        when(positionRepository.findOpenByPortfolio(PORTFOLIO_ID)).thenReturn(List.of());
        when(historicalPricingPort.getPriceSeries(any(), eq("USD"), any(), any()))
                .thenReturn(java.util.Map.of());

        service.autoCloseExpired();

        assertThat(pos.getClosePrice()).isEqualByComparingTo("3.5");
    }

    @Test
    void should_persistSnapshots_when_openingForeignCurrencyPosition() {
        contract.setCurrency("USD");
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(historicalPricingPort.getPriceSeries(any(), eq("USD"), any(), any()))
                .thenReturn(java.util.Map.of(LocalDate.of(2026, 4, 1), new BigDecimal("32.0")));

        service.open(PORTFOLIO_ID, USER_SUB, req);

        verify(historyProvider).fetchAndPersist(eq("F_USDTRY0626"), any(), any());
    }

    @Test
    void should_buildSnapshotsFromCandles_when_backfillHasCandleHistory() {
        OpenDerivativePositionRequest req = new OpenDerivativePositionRequest(
                "F_USDTRY0626", DerivativeDirection.LONG, LocalDate.of(2026, 4, 1),
                new BigDecimal("35.20"), new BigDecimal("1"), null, null);
        when(contractRepository.findBySymbol("F_USDTRY0626")).thenReturn(Optional.of(contract));
        when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ViopCandle candle = ViopCandle.builder()
                .symbol("F_USDTRY0626")
                .candleDate(LocalDate.of(2026, 4, 1).atTime(LocalTime.NOON))
                .close(new BigDecimal("35.60"))
                .build();
        when(candleRepository.findBySymbolAndCandleDateBetweenOrderByCandleDateAsc(
                eq("F_USDTRY0626"), any(), any())).thenReturn(List.of(candle));
        when(snapshotCalculator.buildDerivativeAssetSnapshotAt(any(), any(), any(), any(), any()))
                .thenReturn(com.finance.portfolio.model.PortfolioAssetDailySnapshot.builder().build());

        service.open(PORTFOLIO_ID, USER_SUB, req);

        verify(assetSnapshotRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }
}
