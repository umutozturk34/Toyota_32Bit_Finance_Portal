package com.finance.backend.service;

import com.finance.backend.config.PortfolioProperties;
import com.finance.backend.config.PortfolioProperties.LotLimits;
import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.request.PositionRequest;
import com.finance.backend.dto.response.PortfolioResponse;
import com.finance.backend.dto.response.PositionResponse;
import com.finance.backend.exception.BusinessException;
import com.finance.backend.exception.ResourceNotFoundException;
import com.finance.backend.mapper.PortfolioResponseMapper;
import com.finance.backend.model.AssetType;
import com.finance.backend.model.Portfolio;
import com.finance.backend.model.PortfolioPosition;
import com.finance.backend.repository.PortfolioPositionRepository;
import com.finance.backend.repository.PortfolioRepository;
import com.finance.backend.util.EnumParser;
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
        PortfolioPosition position = PortfolioPosition.builder()
                .portfolio(portfolio)
                .assetType(assetType)
                .assetCode(request.assetCode())
                .quantity(request.quantity())
                .entryDate(request.entryDate())
                .entryPrice(request.entryPrice())
                .build();
        PortfolioPosition saved = positionRepository.save(position);

        publishLotChange(portfolioId, saved.getEntryDate());
        return mapper.toPositionResponseShell(saved);
    }

    @Transactional
    public PositionResponse updatePosition(Long portfolioId, Long positionId, String userSub, PositionRequest request) {
        validateLot(request);
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        LocalDateTime previousEntry = position.getEntryDate();
        position.updateLot(request.entryDate(), request.entryPrice(), request.quantity());
        PortfolioPosition saved = positionRepository.save(position);

        publishLotChange(portfolioId, earliestOf(previousEntry, saved.getEntryDate()));
        return mapper.toPositionResponseShell(saved);
    }

    @Transactional
    public void deletePosition(Long portfolioId, Long positionId, String userSub) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        LocalDateTime entryDate = position.getEntryDate();
        positionRepository.delete(position);
        publishLotChange(portfolioId, entryDate);
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

    private void publishLotChange(Long portfolioId, LocalDateTime fromDate) {
        if (fromDate == null) return;
        eventPublisher.publishEvent(new PortfolioBackfillService.LotChangedEvent(
                portfolioId, fromDate.toLocalDate()));
    }

    private static LocalDateTime earliestOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }
}
