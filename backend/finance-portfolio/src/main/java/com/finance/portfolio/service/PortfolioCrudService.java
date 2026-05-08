package com.finance.portfolio.service;
import com.finance.common.service.MarketSnapshotProcessor;


import com.finance.portfolio.config.PortfolioProperties;
import com.finance.portfolio.config.PortfolioProperties.LotLimits;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
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
import com.finance.portfolio.repository.PortfolioPositionRepository;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.common.util.EnumParser;
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
                .ifPresent(p -> { throw new BusinessException("Portfolio with name '" + request.name() + "' already exists"); });

        Portfolio portfolio = Portfolio.builder().userSub(userSub).name(request.name()).build();
        return mapper.toPortfolioResponse(portfolioRepository.save(portfolio));
    }

    @Transactional
    public PositionResponse addPosition(Long portfolioId, String userSub, PositionRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        validateLot(request);
        AssetType assetType = EnumParser.parseOrBadRequest(AssetType.class,
                request.assetType().toUpperCase(), "asset type");
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

    private PortfolioPosition loadOwnedPosition(Long portfolioId, Long positionId, String userSub) {
        portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
        PortfolioPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
        if (!position.getPortfolioId().equals(portfolioId)) {
            throw new BusinessException("Position does not belong to portfolio: " + portfolioId);
        }
        return position;
    }

    private void validateLot(PositionRequest request) {
        LotLimits limits = portfolioProperties.getLotLimits();
        LocalDate entryDay = request.entryDate() != null ? request.entryDate().toLocalDate() : null;
        if (entryDay != null && limits.getMinEntryDate() != null && entryDay.isBefore(limits.getMinEntryDate())) {
            throw new BusinessException("Giriş tarihi " + limits.getMinEntryDate() + " tarihinden eski olamaz");
        }
        if (entryDay != null && entryDay.isAfter(LocalDate.now())) {
            throw new BusinessException("Giriş tarihi gelecekte olamaz");
        }
        BigDecimal price = request.entryPrice();
        if (price != null && limits.getMinPriceTry() != null && price.compareTo(limits.getMinPriceTry()) < 0) {
            throw new BusinessException("Giriş fiyatı en az " + limits.getMinPriceTry() + " TRY olmalı");
        }
        if (price != null && limits.getMaxPriceTry() != null && price.compareTo(limits.getMaxPriceTry()) > 0) {
            throw new BusinessException("Giriş fiyatı en fazla " + limits.getMaxPriceTry() + " TRY olabilir");
        }
        BigDecimal qty = request.quantity();
        if (qty != null && limits.getMinQuantity() != null && qty.compareTo(limits.getMinQuantity()) < 0) {
            throw new BusinessException("Miktar en az " + limits.getMinQuantity() + " olmalı");
        }
        if (qty != null && limits.getMaxQuantity() != null && qty.compareTo(limits.getMaxQuantity()) > 0) {
            throw new BusinessException("Miktar en fazla " + limits.getMaxQuantity() + " olabilir");
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
                        "Pozisyon eklemek için bu varlık önce takip listesine alınmalı: "
                                + assetType + " / " + normalizedCode));
    }
}
