package com.finance.portfolio.service.performance;

import com.finance.portfolio.service.pricing.RealReturnCalculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Per-currency frame inputs for ONE chart point: the NOTIONAL value (Σ per-asset market value at the
 * date), direction-aware footprints for the whole point, and the same footprints grouped by asset type
 * for the per-type (K/Z contribution) detail frame.
 */
record PerCcyInputs(BigDecimal notionalTry, List<RealReturnCalculator.EntryFootprint> fps,
                    Map<String, List<RealReturnCalculator.EntryFootprint>> fpsByType) {}
