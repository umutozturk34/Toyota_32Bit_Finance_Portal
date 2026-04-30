package com.finance.backend.service;

import com.finance.backend.dto.request.PortfolioCreateRequest;
import com.finance.backend.dto.request.PositionRequest;
import com.finance.backend.dto.response.PortfolioResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioCrudService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final PortfolioResponseMapper mapper;

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
    public PortfolioPosition addPosition(Long portfolioId, String userSub, PositionRequest request) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserSub(portfolioId, userSub)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found: " + portfolioId));
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
        return positionRepository.save(position);
    }

    @Transactional
    public PortfolioPosition updatePosition(Long portfolioId, Long positionId, String userSub, PositionRequest request) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        position.updateLot(request.entryDate(), request.entryPrice(), request.quantity());
        return positionRepository.save(position);
    }

    @Transactional
    public void deletePosition(Long portfolioId, Long positionId, String userSub) {
        PortfolioPosition position = loadOwnedPosition(portfolioId, positionId, userSub);
        positionRepository.delete(position);
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
}
