package com.finance.portfolio.derivative.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.viop.model.ViopContract;
import com.finance.market.viop.repository.ViopContractRepository;
import com.finance.portfolio.derivative.dto.request.CloseDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.OpenDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.request.UpdateDerivativePositionRequest;
import com.finance.portfolio.derivative.dto.response.DerivativePositionResponse;
import com.finance.portfolio.derivative.mapper.DerivativePositionMapper;
import com.finance.portfolio.derivative.model.DerivativeCloseReason;
import com.finance.portfolio.derivative.model.DerivativePosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.portfolio.service.PortfolioBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class DerivativePositionService {

    private final DerivativePositionRepository positionRepository;
    private final PortfolioRepository portfolioRepository;
    private final ViopContractRepository contractRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final DerivativePositionMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DerivativeSnapshotMaintenance snapshotMaintenance;
    private final DerivativePriceResolver priceResolver;

    private void publishLotChange(Long portfolioId, DerivativePosition position, LocalDate from) {
        if (from == null) return;
        String symbol = position.getViopContract() != null ? position.getViopContract().getSymbol() : null;
        if (symbol == null) return;
        eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                portfolioId, AssetType.VIOP, symbol, from, true));
    }

    @Transactional(readOnly = true)
    public List<DerivativePositionResponse> list(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        return mapper.toResponses(positionRepository.findByPortfolioId(portfolioId));
    }

    @Transactional(readOnly = true)
    public List<DerivativePositionResponse> listOpen(Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        return mapper.toResponses(positionRepository.findOpenByPortfolio(portfolioId));
    }

    @Transactional
    public DerivativePositionResponse open(Long portfolioId, String userSub, OpenDerivativePositionRequest request) {
        Portfolio portfolio = requireOwnedPortfolio(portfolioId, userSub);
        ViopContract contract = contractRepository.findBySymbol(request.contractSymbol())
                .orElseThrow(() -> new ResourceNotFoundException("error.viop.contractNotFound", request.contractSymbol()));
        if (!contract.isActive()) {
            throw new BadRequestException("error.viop.contractInactive", request.contractSymbol());
        }
        if (contract.getLastPrice() == null) {
            throw new BadRequestException("error.viop.contractNotTradeable", request.contractSymbol());
        }
        if (contract.getExpiryDate() != null && request.entryDate().isAfter(contract.getExpiryDate())) {
            throw new BadRequestException("error.viop.entryAfterExpiry", request.contractSymbol());
        }
        BigDecimal entryPrice = request.entryPrice() != null
                ? request.entryPrice()
                : priceResolver.resolveHistoricalPriceTry(contract, request.entryDate());
        if (entryPrice == null) {
            throw new BadRequestException("error.viop.entryPriceUnavailable", request.contractSymbol());
        }
        DerivativePosition position = DerivativePosition.builder()
                .portfolio(portfolio)
                .viopContract(contract)
                .direction(request.direction())
                .entryDate(request.entryDate())
                .entryPrice(entryPrice)
                .quantityLot(request.quantityLot())
                .build();
        if (request.closeDate() != null) {
            BigDecimal closePrice = request.closePrice() != null
                    ? request.closePrice()
                    : priceResolver.resolveHistoricalPriceTry(contract, request.closeDate());
            if (closePrice == null) {
                throw new BadRequestException("error.viop.closePriceUnavailable", request.contractSymbol());
            }
            position.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
        }
        DerivativePosition saved = positionRepository.save(position);
        snapshotMaintenance.backfillSnapshots(saved);
        snapshotMaintenance.consolidateSymbolSnapshots(portfolioId, contract.getSymbol());
        publishLotChange(portfolioId, saved, saved.getEntryDate());
        log.info("DerivativePosition opened portfolio={} contract={} direction={} qty={}",
                portfolioId, contract.getSymbol(), request.direction(), request.quantityLot());
        return mapper.toResponse(saved);
    }

    @Transactional
    public DerivativePositionResponse close(Long positionId, Long portfolioId, String userSub,
                                            CloseDerivativePositionRequest request) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (!position.isOpen()) {
            throw new BadRequestException("error.derivative.alreadyClosed", positionId);
        }
        if (position.getEntryDate() != null && request.closeDate().isBefore(position.getEntryDate())) {
            throw new BadRequestException("error.derivative.closeBeforeEntry", positionId);
        }
        BigDecimal closePrice = request.closePrice() != null
                ? request.closePrice()
                : priceResolver.resolveHistoricalPriceTry(position.getViopContract(), request.closeDate());
        if (closePrice == null) {
            throw new BadRequestException("error.viop.closePriceUnavailable",
                    position.getViopContract().getSymbol());
        }

        BigDecimal closeQty = request.closeQuantityLot();
        boolean partial = closeQty != null
                && closeQty.signum() > 0
                && closeQty.compareTo(position.getQuantityLot()) < 0;
        if (closeQty != null && closeQty.compareTo(position.getQuantityLot()) > 0) {
            throw new BadRequestException("error.derivative.closeQtyExceedsPosition", positionId);
        }

        DerivativePosition primary = partial
                ? splitForPartialClose(position, closeQty, request.closeDate(), closePrice)
                : closeFull(position, request.closeDate(), closePrice);
        String symbol = position.getViopContract().getSymbol();
        rebuildPeerSnapshots(portfolioId, symbol, primary, partial ? position : null);
        publishLotChange(portfolioId, primary, primary.getEntryDate());
        log.info("DerivativePosition closed id={} portfolio={} closeDate={} partial={}",
                positionId, portfolioId, request.closeDate(), partial);
        return mapper.toResponse(primary);
    }

    private DerivativePosition splitForPartialClose(DerivativePosition position, BigDecimal closeQty,
                                                     LocalDate closeDate, BigDecimal closePrice) {
        DerivativePosition closedSlice = DerivativePosition.builder()
                .portfolio(position.getPortfolio())
                .viopContract(position.getViopContract())
                .direction(position.getDirection())
                .entryDate(position.getEntryDate())
                .entryPrice(position.getEntryPrice())
                .quantityLot(closeQty)
                .build();
        closedSlice.closeWith(closeDate, closePrice, DerivativeCloseReason.USER_CLOSED);
        positionRepository.save(closedSlice);
        position.reduceQuantity(closeQty);
        positionRepository.save(position);
        return closedSlice;
    }

    private DerivativePosition closeFull(DerivativePosition position, LocalDate closeDate, BigDecimal closePrice) {
        position.closeWith(closeDate, closePrice, DerivativeCloseReason.USER_CLOSED);
        return position;
    }

    private void rebuildPeerSnapshots(Long portfolioId, String symbol, DerivativePosition primary,
                                       DerivativePosition reduced) {
        assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                portfolioId, AssetType.VIOP, symbol);
        snapshotMaintenance.backfillSnapshots(primary);
        if (reduced != null) snapshotMaintenance.backfillSnapshots(reduced);
        for (DerivativePosition remaining : positionRepository.findByPortfolioId(portfolioId)) {
            if (remaining.getViopContract() == null) continue;
            if (!symbol.equals(remaining.getViopContract().getSymbol())) continue;
            Long rid = remaining.getId();
            if (rid.equals(primary.getId())) continue;
            if (reduced != null && rid.equals(reduced.getId())) continue;
            snapshotMaintenance.backfillSnapshots(remaining);
        }
        snapshotMaintenance.consolidateSymbolSnapshots(portfolioId, symbol);
    }

    @Transactional
    public DerivativePositionResponse updateClose(Long positionId, Long portfolioId, String userSub,
                                                   CloseDerivativePositionRequest request) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (position.isOpen()) {
            throw new BadRequestException("error.derivative.notClosed", positionId);
        }
        if (position.getEntryDate() != null && request.closeDate().isBefore(position.getEntryDate())) {
            throw new BadRequestException("error.derivative.closeBeforeEntry", positionId);
        }
        BigDecimal closePrice = request.closePrice() != null
                ? request.closePrice()
                : priceResolver.resolveHistoricalPriceTry(position.getViopContract(), request.closeDate());
        if (closePrice == null) {
            throw new BadRequestException("error.viop.closePriceUnavailable",
                    position.getViopContract().getSymbol());
        }
        position.reopenForUpdate();
        position.closeWith(request.closeDate(), closePrice, DerivativeCloseReason.USER_CLOSED);
        rebuildPeerSnapshots(portfolioId, position.getViopContract().getSymbol(), position, null);
        log.info("DerivativePosition close updated id={} portfolio={} newCloseDate={}",
                positionId, portfolioId, request.closeDate());
        return mapper.toResponse(position);
    }

    @Transactional
    public DerivativePositionResponse updateOpen(Long positionId, Long portfolioId, String userSub,
                                                  UpdateDerivativePositionRequest request) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (!position.isOpen()) {
            throw new BadRequestException("error.derivative.alreadyClosed", positionId);
        }
        ViopContract contract = position.getViopContract();
        if (contract.getExpiryDate() != null && request.entryDate().isAfter(contract.getExpiryDate())) {
            throw new BadRequestException("error.viop.entryAfterExpiry", contract.getSymbol());
        }
        BigDecimal entryPrice = request.entryPrice() != null
                ? request.entryPrice()
                : priceResolver.resolveHistoricalPriceTry(contract, request.entryDate());
        if (entryPrice == null) {
            throw new BadRequestException("error.viop.entryPriceUnavailable", contract.getSymbol());
        }
        position.updateEntry(request.direction(), request.entryDate(), entryPrice, request.quantityLot());
        rebuildPeerSnapshots(portfolioId, contract.getSymbol(), position, null);
        publishLotChange(portfolioId, position, position.getEntryDate());
        log.info("DerivativePosition entry updated id={} portfolio={} entryDate={} qty={}",
                positionId, portfolioId, request.entryDate(), request.quantityLot());
        return mapper.toResponse(position);
    }

    @Transactional
    public DerivativePositionResponse reopen(Long positionId, Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        if (position.isOpen()) {
            throw new BadRequestException("error.derivative.notClosed", positionId);
        }
        position.reopenForUpdate();
        rebuildPeerSnapshots(portfolioId, position.getViopContract().getSymbol(), position, null);
        publishLotChange(portfolioId, position, position.getEntryDate());
        log.info("DerivativePosition reopened id={} portfolio={}", positionId, portfolioId);
        return mapper.toResponse(position);
    }

    @Transactional
    public void delete(Long positionId, Long portfolioId, String userSub) {
        requireOwnedPortfolio(portfolioId, userSub);
        DerivativePosition position = positionRepository.findByIdAndPortfolioId(positionId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.derivative.positionNotFound", positionId));
        String symbol = position.getViopContract() != null ? position.getViopContract().getSymbol() : null;
        LocalDate fromDate = position.getEntryDate();
        positionRepository.delete(position);
        if (symbol != null) {
            assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                    portfolioId, AssetType.VIOP, symbol);
            for (DerivativePosition remaining : positionRepository.findByPortfolioId(portfolioId)) {
                if (remaining.getViopContract() != null
                        && symbol.equals(remaining.getViopContract().getSymbol())) {
                    snapshotMaintenance.backfillSnapshots(remaining);
                }
            }
            snapshotMaintenance.consolidateSymbolSnapshots(portfolioId, symbol);
            if (fromDate != null) {
                eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                        portfolioId, AssetType.VIOP, symbol, fromDate, true));
            }
        }
        log.info("DerivativePosition deleted id={} portfolio={}", positionId, portfolioId);
    }

    @Transactional
    public int autoCloseExpired() {
        List<DerivativePosition> orphaned = positionRepository.findOpenWithExpiredContract(LocalDate.now());
        int closed = 0;
        for (DerivativePosition pos : orphaned) {
            ViopContract contract = pos.getViopContract();
            BigDecimal settlementNative = contract.getSettlementPrice() != null
                    ? contract.getSettlementPrice()
                    : contract.getLastPrice();
            if (settlementNative == null) continue;
            BigDecimal settlementTry = priceResolver.nativeToTryOnDate(settlementNative,
                    contract.getCurrency(), contract.getExpiryDate());
            pos.closeWith(contract.getExpiryDate(), settlementTry, DerivativeCloseReason.EXPIRED);
            rebuildPeerSnapshots(pos.getPortfolio().getId(), contract.getSymbol(), pos, null);
            closed++;
        }
        if (closed > 0) {
            log.info("Auto-closed {} expired derivative positions", closed);
        }
        return closed;
    }

    private Portfolio requireOwnedPortfolio(Long portfolioId, String userSub) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        if (!portfolio.getUserSub().equals(userSub)) {
            throw new ResourceNotFoundException("error.portfolio.notFound", portfolioId);
        }
        return portfolio;
    }
}
