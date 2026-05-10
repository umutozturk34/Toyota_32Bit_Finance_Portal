package com.finance.notification.watchlist.service;

import com.finance.common.exception.BadRequestException;
import com.finance.common.exception.ResourceNotFoundException;
import com.finance.notification.config.WatchlistManagementProperties;
import com.finance.notification.watchlist.dto.WatchlistCreateRequest;
import com.finance.notification.watchlist.dto.WatchlistRenameRequest;
import com.finance.notification.watchlist.dto.WatchlistResponse;
import com.finance.notification.watchlist.model.Watchlist;
import com.finance.notification.watchlist.repository.WatchlistItemRepository;
import com.finance.notification.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class WatchlistManagementService {

    private final WatchlistRepository repository;
    private final WatchlistItemRepository itemRepository;
    private final WatchlistManagementProperties properties;

    @Transactional
    public Watchlist ensureDefault(String userSub) {
        return repository.findByUserSubAndIsDefaultTrue(userSub)
                .orElseGet(() -> repository.save(Watchlist.createDefault(userSub)));
    }

    @Transactional
    public List<WatchlistResponse> list(String userSub) {
        List<Watchlist> watchlists = repository.findByUserSubOrderByIsDefaultDescCreatedAtAsc(userSub);
        if (watchlists.isEmpty()) {
            watchlists = List.of(repository.save(Watchlist.createDefault(userSub)));
        }
        return watchlists.stream()
                .map(w -> new WatchlistResponse(
                        w.getId(),
                        w.getName(),
                        w.isDefault(),
                        itemRepository.countByWatchlistId(w.getId()),
                        w.getCreatedAt(),
                        w.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public WatchlistResponse create(String userSub, WatchlistCreateRequest request) {
        long existing = repository.countByUserSub(userSub);
        if (existing >= properties.maxPerUser()) {
            throw new BadRequestException("error.watchlist.maxReached", properties.maxPerUser());
        }
        String trimmed = request.name().trim();
        if (repository.existsByUserSubAndName(userSub, trimmed)) {
            throw new BadRequestException("error.watchlist.duplicateName");
        }
        Watchlist saved = repository.save(Watchlist.create(userSub, trimmed));
        log.info("Watchlist created userSub={} watchlistId={} name={}",
                userSub, saved.getId(), saved.getName());
        return new WatchlistResponse(saved.getId(), saved.getName(), saved.isDefault(), 0L,
                saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional
    public WatchlistResponse rename(Long id, String userSub, WatchlistRenameRequest request) {
        Watchlist watchlist = ownedOr404(id, userSub);
        String trimmed = request.name().trim();
        if (!watchlist.getName().equals(trimmed)
                && repository.existsByUserSubAndName(userSub, trimmed)) {
            throw new BadRequestException("error.watchlist.duplicateName");
        }
        watchlist.rename(trimmed);
        Watchlist saved = repository.save(watchlist);
        log.info("Watchlist renamed userSub={} watchlistId={} name={}",
                userSub, saved.getId(), saved.getName());
        return new WatchlistResponse(saved.getId(), saved.getName(), saved.isDefault(),
                itemRepository.countByWatchlistId(saved.getId()),
                saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Transactional
    public void delete(Long id, String userSub) {
        Watchlist watchlist = ownedOr404(id, userSub);
        if (watchlist.isDefault()) {
            throw new BadRequestException("error.watchlist.defaultLocked");
        }
        repository.delete(watchlist);
        log.info("Watchlist deleted watchlistId={} userSub={}", id, userSub);
    }

    @Transactional(readOnly = true)
    public Watchlist requireOwned(Long id, String userSub) {
        return ownedOr404(id, userSub);
    }

    private Watchlist ownedOr404(Long id, String userSub) {
        return repository.findById(id)
                .filter(w -> w.belongsTo(userSub))
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist not found id=" + id));
    }
}
