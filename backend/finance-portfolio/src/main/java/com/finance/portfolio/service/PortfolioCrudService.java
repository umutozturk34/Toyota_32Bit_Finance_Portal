package com.finance.portfolio.service;


import com.finance.common.exception.BusinessException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.TrackedAsset;
import com.finance.common.model.TrackedAssetType;
import com.finance.common.repository.TrackedAssetRepository;
import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.derivative.repository.DerivativePositionRepository;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.request.PositionSellRequest;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.dto.response.PositionResponse;
import com.finance.portfolio.mapper.PortfolioResponseMapper;
import com.finance.portfolio.model.AssetType;
import com.finance.portfolio.model.Portfolio;
import com.finance.portfolio.model.PortfolioPosition;
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
        PortfolioValidator.validateLot(request, portfolioProperties.getLotLimits());
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
        if (request.exitDate() != null && request.exitPrice() != null) {
            position.closeWith(request.exitDate(), request.exitPrice());
        }
        PortfolioPosition saved = positionRepository.save(position);

        publishLotChange(portfolioId, saved, saved.getEntryDate(), true);
        return mapper.toPositionResponseShell(saved);
    }

    @Transactional
    public PositionResponse updatePosition(Long portfolioId, Long positionId, String userSub, PositionRequest request) {
        PortfolioValidator.validateLot(request, portfolioProperties.getLotLimits());
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
        Long trackedAssetId = position.getTrackedAsset() != null
                ? position.getTrackedAsset().getId() : null;
        boolean hasOtherLot = trackedAssetId != null && positionRepository
                .existsByPortfolioIdAndTrackedAsset_IdAndIdNot(portfolioId, trackedAssetId, positionId);
        positionRepository.delete(position);
        if (!hasOtherLot) {
            assetSnapshotRepository.deleteByPortfolioIdAndAssetTypeAndAssetCode(
                    portfolioId, position.getAssetType(), position.getAssetCode());
        }
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
        PortfolioValidator.validateSell(position, request);

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
