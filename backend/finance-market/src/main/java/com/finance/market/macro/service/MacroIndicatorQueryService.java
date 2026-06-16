package com.finance.market.macro.service;

import com.finance.common.exception.ResourceNotFoundException;
import com.finance.market.macro.model.MacroCategory;
import com.finance.market.macro.model.MacroIndicator;
import com.finance.market.macro.model.MacroIndicatorPoint;
import com.finance.market.macro.repository.MacroIndicatorPointRepository;
import com.finance.market.macro.repository.MacroIndicatorRepository;
import com.finance.market.macro.util.MacroSlug;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Read API for macro indicators and their observation history (by category, prominence, or code). */
@Service
@RequiredArgsConstructor
public class MacroIndicatorQueryService {

    private final MacroIndicatorRepository indicatorRepository;
    private final MacroIndicatorPointRepository pointRepository;

    @Transactional(readOnly = true)
    public List<MacroIndicator> listAll() {
        return indicatorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<MacroIndicator> listProminent() {
        return indicatorRepository.findByProminentTrueOrderByCategoryAsc();
    }

    @Transactional(readOnly = true)
    public List<MacroIndicator> listByCategory(MacroCategory category) {
        return indicatorRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public MacroIndicator findByCode(String code) {
        return indicatorRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.macro.indicatorNotFound", code));
    }

    /**
     * Resolves an indicator from its PUBLIC id — the EVDS-free slug the frontend uses. Falls back to the raw
     * EVDS code (starts with {@code TP.}) so internal/legacy code-based lookups keep working. This is how the
     * raw EVDS code stays backend-only while the API speaks slugs.
     */
    @Transactional(readOnly = true)
    public MacroIndicator findByPublicId(String publicId) {
        if (publicId != null && publicId.startsWith("TP.")) {
            return findByCode(publicId);
        }
        return indicatorRepository.findAll().stream()
                .filter(i -> i.getLabel() != null && MacroSlug.slugify(i.getLabel()).equals(publicId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "error.macro.indicatorNotFound", publicId));
    }

    @Transactional(readOnly = true)
    public List<MacroIndicatorPoint> history(MacroIndicator indicator, LocalDate from, LocalDate to) {
        return pointRepository.findByIndicatorAndObservedAtBetweenOrderByObservedAtAsc(
                indicator, from, to);
    }
}
