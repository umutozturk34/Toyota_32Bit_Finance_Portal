package com.finance.notification.alert.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.common.model.MarketType;
import com.finance.notification.alert.dto.PriceAlertCreateRequest;
import com.finance.notification.alert.dto.PriceAlertResponse;
import com.finance.notification.alert.mapper.PriceAlertMapper;
import com.finance.notification.alert.model.PriceAlert;
import com.finance.notification.alert.repository.PriceAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertRepository repository;
    private final PriceAlertMapper mapper;

    @Transactional
    public PriceAlertResponse create(String userSub, PriceAlertCreateRequest request) {
        PriceAlert entity = mapper.toEntity(request, userSub);
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<PriceAlertResponse> list(String userSub, int page, int size) {
        return repository.findByUserSubOrderByCreatedAtDesc(userSub, PageRequest.of(page, size))
                .map(mapper::toResponse);
    }

    @Transactional
    public void delete(Long id, String userSub) {
        PriceAlert alert = ownedOr404(id, userSub);
        repository.delete(alert);
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> activeAlerts(MarketType marketType) {
        return repository.findByActiveTrueAndMarketType(marketType);
    }

    @Transactional
    public void persist(PriceAlert alert) {
        repository.save(alert);
    }

    private PriceAlert ownedOr404(Long id, String userSub) {
        return repository.findById(id)
                .filter(a -> a.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException("Price alert not found id=" + id));
    }
}
