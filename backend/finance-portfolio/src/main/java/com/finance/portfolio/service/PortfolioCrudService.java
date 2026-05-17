package com.finance.portfolio.service;


import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioPosition;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.repository.PortfolioAssetDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioDailySnapshotRepository;
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.shared.util.EnumParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioCrudService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioDailySnapshotRepository dailySnapshotRepository;
    private final PortfolioAssetDailySnapshotRepository assetSnapshotRepository;
    private final DerivativePositionRepository derivativePositionRepository;
    private final TrackedAssetRepository trackedAssetRepository;
    private final PortfolioResponseMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PortfolioProperties portfolioProperties;

    @Transactional(readOnly = true)
    public List<PortfolioResponse> listPortfolios(String userSub) {
        return portfolioRepository.findByUserSub(userSub).stream()
                .map(mapper::toPortfolioResponse)
                .toList();
    }

    @Transactional
    public PortfolioResponse createPortfolio(String userSub, PortfolioCreateRequest request) {
        portfolioRepository.findByUserSubAndName(userSub, request.name())
                .ifPresent(p -> { throw new BusinessException("error.portfolio.duplicateName", request.name()); });

        Portfolio portfolio = Portfolio.builder().userSub(userSub).name(request.name()).build();
        return mapper.toPortfolioResponse(portfolioRepository.save(portfolio));
    }

    @Transactional
    public PortfolioResponse renamePortfolio(String userSub, Long portfolioId, PortfolioCreateRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        portfolioRepository.findByUserSubAndName(userSub, request.name())
                .filter(p -> !p.getId().equals(portfolioId))
                .ifPresent(p -> { throw new BusinessException("error.portfolio.duplicateName", request.name()); });
        portfolio.setName(request.name());
        return mapper.toPortfolioResponse(portfolioRepository.save(portfolio));
    }

    @Transactional
    public void deletePortfolio(String userSub, Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        derivativePositionRepository.deleteByPortfolio_Id(portfolioId);
        positionRepository.deleteByPortfolioId(portfolioId);
        assetSnapshotRepository.deleteByPortfolioId(portfolioId);
        dailySnapshotRepository.deleteByPortfolioId(portfolioId);
        portfolioRepository.delete(portfolio);
    }

    @Transactional
    public PositionResponse addPosition(Long portfolioId, String userSub, PositionRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        validateLot(request);
        AssetType assetType = EnumParser.parseOrBadRequest(AssetType.class,
                request.assetType().toUpperCase(), "enum.field.assetType");
        TrackedAsset trackedAsset = requireTrackedAsset(assetType, request.assetCode());
        PortfolioPosition position = PortfolioPosition.builder()
                .portfolio(portfolio)
                .assetType(assetType)
                .assetCode(trackedAsset.getAssetCode())
                .quantity(request.quantity())
                .entryDate(request.entryDate())
                .entryPrice(request.entryPrice())
                .build();
        position.setTrackedAsset(trackedAsset);
        PortfolioPosition saved = positionRepository.save(position);

        publishLotChange(portfolioId, saved, saved.getEntryDate(), true);
        return mapper.toPositionResponseShell(saved);
    }

    @Transactional
    public PositionResponse updatePosition(Long portfolioId, Long positionId, String userSub, PositionRequest request) {
        validateLot(request);
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        LocalDateTime previousEntry = position.getEntryDate();
        position.updateLot(request.entryDate(), request.entryPrice(), request.quantity());
        PortfolioPosition saved = positionRepository.save(position);

        publishLotChange(portfolioId, saved, earliestOf(previousEntry, saved.getEntryDate()), true);
        return mapper.toPositionResponseShell(saved);
    }

    @Transactional
    public void deletePosition(Long portfolioId, Long positionId, String userSub) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        LocalDateTime entryDate = position.getEntryDate();
        positionRepository.delete(position);
        publishLotChange(portfolioId, position, entryDate, false);
    }

    @Transactional
    public PositionResponse sellPosition(Long portfolioId, Long positionId, String userSub, PositionSellRequest request) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        if (position.getAssetType() == AssetType.VIOP) {
            throw new BusinessException("error.portfolio.sell.useDerivativeClose");
        }
        if (position.isClosed()) {
            throw new BusinessException("error.portfolio.sell.alreadyClosed");
        }
        validateSell(position, request);

        BigDecimal sellQty = request.quantity();
        PortfolioPosition resulting;
        if (sellQty.compareTo(position.getQuantity()) == 0) {
            position.closeWith(request.exitDate(), request.exitPrice());
            resulting = positionRepository.save(position);
        } else {
            PortfolioPosition closedSlice = PortfolioPosition.builder()
                    .portfolio(position.getPortfolio())
                    .assetType(position.getAssetType())
                    .assetCode(position.getAssetCode())
                    .quantity(sellQty)
                    .entryDate(position.getEntryDate())
                    .entryPrice(position.getEntryPrice())
                    .exitDate(request.exitDate())
                    .exitPrice(request.exitPrice())
                    .build();
            closedSlice.setTrackedAsset(position.getTrackedAsset());
            positionRepository.save(closedSlice);
            position.updateLot(null, null, position.getQuantity().subtract(sellQty));
            resulting = positionRepository.save(position);
        }

        publishLotChange(portfolioId, position, position.getEntryDate(), true);
        return mapper.toPositionResponseShell(resulting);
    }

    @Transactional
    public PositionResponse reopenPosition(Long portfolioId, Long positionId, String userSub) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        if (!position.isClosed()) {
            throw new BusinessException("error.portfolio.reopen.notClosed");
        }
        position.reopen();
        PortfolioPosition saved = positionRepository.save(position);
        publishLotChange(portfolioId, position, position.getEntryDate(), true);
        return mapper.toPositionResponseShell(saved);
    }

    private void validateSell(PortfolioPosition position, PositionSellRequest request) {
        if (request.quantity().compareTo(position.getQuantity()) > 0) {
            throw new BusinessException("error.portfolio.sell.quantityExceedsPosition");
        }
        LocalDate exitDay = request.exitDate().toLocalDate();
        if (exitDay.isBefore(position.getEntryDate().toLocalDate())) {
            throw new BusinessException("error.portfolio.sell.dateBeforeEntry");
        }
        if (exitDay.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.sell.dateInFuture");
        }
    }

    private PortfolioPosition loadOwnedPosition(Long portfolioId, Long positionId, String userSub) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.notFound", portfolioId));
        PortfolioPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.portfolio.position.notFound", positionId));
        if (!position.getPortfolioId().equals(portfolioId)) {
            throw new BusinessException("error.portfolio.position.notInPortfolio", portfolioId);
        }
        return position;
    }

    private void validateLot(PositionRequest request) {
        LotLimits limits = portfolioProperties.getLotLimits();
        LocalDate entryDay = request.entryDate() != null ? request.entryDate().toLocalDate() : null;
        if (entryDay != null && limits.getMinEntryDate() != null && entryDay.isBefore(limits.getMinEntryDate())) {
            throw new BusinessException("error.portfolio.lot.entryDateTooOld", limits.getMinEntryDate());
        }
        if (entryDay != null && entryDay.isAfter(LocalDate.now())) {
            throw new BusinessException("error.portfolio.lot.entryDateInFuture");
        }
        BigDecimal price = request.entryPrice();
        if (price != null && limits.getMinPriceTry() != null && price.compareTo(limits.getMinPriceTry()) < 0) {
            throw new BusinessException("error.portfolio.lot.priceTooLow", limits.getMinPriceTry());
        }
        if (price != null && limits.getMaxPriceTry() != null && price.compareTo(limits.getMaxPriceTry()) > 0) {
            throw new BusinessException("error.portfolio.lot.priceTooHigh", limits.getMaxPriceTry());
        }
        BigDecimal qty = request.quantity();
        if (qty != null && limits.getMinQuantity() != null && qty.compareTo(limits.getMinQuantity()) < 0) {
            throw new BusinessException("error.portfolio.lot.quantityTooLow", limits.getMinQuantity());
        }
        if (qty != null && limits.getMaxQuantity() != null && qty.compareTo(limits.getMaxQuantity()) > 0) {
            throw new BusinessException("error.portfolio.lot.quantityTooHigh", limits.getMaxQuantity());
        }
    }

    private void publishLotChange(Long portfolioId, PortfolioPosition position, LocalDateTime fromDate, boolean visibleToUi) {
        if (fromDate == null) return;
        eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                portfolioId, position.getAssetType(), position.getAssetCode(), fromDate.toLocalDate(), visibleToUi));
    }

    private static LocalDateTime earliestOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private TrackedAsset requireTrackedAsset(AssetType assetType, String rawCode) {
        TrackedAssetType trackedType = TrackedAssetType.valueOf(assetType.name());
        String normalizedCode = trackedType.normalizeCode(rawCode);
        return trackedAssetRepository
                .findByAssetTypeAndAssetCodeIgnoreCase(trackedType, normalizedCode)
                .orElseThrow(() -> new BusinessException(
                        "error.portfolio.assetNotTracked", assetType, normalizedCode));
    }
}
