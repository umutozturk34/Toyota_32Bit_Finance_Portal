import { useMemo } from 'react';
import { useQueries } from '@tanstack/react-query';
import useSessionState from '../../../shared/hooks/useSessionState';
import { RANGES } from '../../macro/constants';
import { buildOption, computeSharedBaselineDate } from '../lib/compareChartBuilder';
import {
  isMacro,
  isRateLike,
  rangeBoundsCalendar,
  fetchSeries,
  colorFor,
  forwardFillTo,
  forwardFillDaily,
  backFillToWindowStart,
  displayLabel,
  nativeCurrencyFor,
  compoundRateSeries,
  deriveRateSeries,
} from '../lib/compareSeriesUtils';

// The compare page's data pipeline: range -> bounds -> per-series fetch -> classify/compound -> backfill ->
// FX-convert -> normalize -> chart option. Extracted verbatim from ComparePage so the page stays a thin shell;
// every useMemo body, dependency array, and eslint-disable comment is preserved exactly to keep behavior
// identical (the frontend has no unit tests to catch a subtle drift).
export default function useCompareSeries({
  rangeId,
  useExplicitBounds,
  customFrom,
  customTo,
  selected,
  t,
  macroLabelByCode,
  macroUnitByCode,
  macroListPending,
  originalView,
  targetCurrency,
  convertBetween,
  isDark,
}) {
  const range = useMemo(() => RANGES.find((r) => r.id === rangeId) || RANGES[3], [rangeId]);
  const bounds = useMemo(() => {
    if (useExplicitBounds && customFrom && customTo) {
      return { from: customFrom, to: customTo };
    }
    return rangeBoundsCalendar(range.id);
  }, [range, useExplicitBounds, customFrom, customTo]);

  const queries = useQueries({
    queries: selected.map((s) => ({
      queryKey: ['compare-page-history', s.type, s.code, bounds.from, bounds.to],
      queryFn: () => fetchSeries(s, bounds),
      enabled: !!s.code,
      staleTime: 5 * 60 * 1000,
    })),
  });
  const isLoading = queries.some((q) => q.isLoading);

  // A "rate-vs-rate" compare (every selected series is a PERCENT-unit rate/deposit) can be read two ways: as
  // the actual rate LEVEL over time (how interest rose/fell) or as a cumulative growth index. Only then do the
  // series share one % axis; mixing an asset or a CPI-index series forces the normalized % view.
  // Eligible when EVERY selected series is a macro rate/inflation series (deposits, policy/reference rates,
  // and CPI/PPI/index-rates) — i.e. no assets or portfolio. Each can be expressed as an annual rate, so the
  // "Yıllık" mode is meaningful and stays like-for-like: rate series show their raw annual %, index series
  // show their derived YoY %. (Mixing an asset forces the normalized cumulative view — no annual toggle.)
  const homogeneousRates = useMemo(
    () => selected.length >= 2 && selected.every((s) =>
      s.type === 'MACRO_DEPOSIT' || s.type === 'MACRO_RATE' || s.type === 'MACRO_INFLATION'),
    [selected],
  );
  // Default to Cumulative: a deposit/rate compare's meaningful headline is the compounded growth (a rising
  // curve), not the raw annual-rate LEVEL (which dips when the rate itself falls and reads as "money going
  // down"). Level stays one click away for inspecting the rate trajectory.
  const [valueMode, setValueMode] = useSessionState('compare:valueMode', 'cumulative');
  const levelMode = homogeneousRates && valueMode === 'level';

  // Right edge for rate compounding and forward-fill. Local-zone sv-SE (never UTC toISOString, which
  // shifts a day in non-Istanbul / pre-03:00 zones). With explicit bounds (Beater hand-off pinning a
  // past window) the tail stops at bounds.to, otherwise it runs to today.
  const fillUntil = useMemo(
    () => (useExplicitBounds ? bounds.to : new Date().toLocaleDateString('sv-SE')),
    [useExplicitBounds, bounds.to],
  );

  const rawSeriesData = useMemo(
    () => selected.map((ind, idx) => {
      const raw = queries[idx]?.data || [];
      // In comparison, a rate series (deposit interest, TLREF/policy rate) is compounded into a
      // cumulative growth curve so it ranks against assets' returns. But a MACRO_RATE already expressed
      // as an INDEX (e.g. the BIST TLREF Index, ~6022) is ALREADY cumulative — compounding it again
      // explodes the value, so index-unit rates follow the price path (raw levels, normalized from
      // baseline). Mirrors backend ScenarioService.shouldCompound: compound only when unit == PERCENT.
      // Unit-first: the unit carried on the selection (set at add / restored from URL / backfilled from the
      // macro list) wins so the classification is reliable; macroUnitByCode is only a fallback for legacy
      // selections that never carried one. A PERCENT MACRO_RATE (policy/TLREF rate) MUST compound — without
      // a resolved unit it would fall to the raw-level branch and a positive rate would ratio-rebase into a
      // spurious shrink (−24.94%) instead of a growing money index.
      const macroUnit = ind.unit ?? macroUnitByCode[ind.code];
      // Safety net for the URL-reload race AND for a /macro-indicators failure: a MACRO_RATE is the only
      // ambiguous unit (PERCENT vs INDEX), so when its unit is still unknown AND the unit map is unresolved,
      // hold the series (empty points) rather than guess a classification — a PERCENT rate must never be
      // silently shown as a raw INDEX level-ratio (the −24.94% spurious-shrink bug). "Unresolved" is BOTH
      // still-pending AND settled-but-empty (errored / no units): an old/bookmarked link with no `units` param
      // would otherwise fall through to the raw-level branch the instant the list query ERRORED, mis-plotting
      // a positive annual rate as a shrinking level. A MACRO_RATE that carries its OWN unit (added from search
      // or restored from the URL `units` param) has !macroUnit === false, so it is NEVER gated and renders
      // correctly even on a list error — only a truly-unresolved one is held (a missing line beats a wrong
      // line). DEPOSIT always compounds and INFLATION is always an INDEX, so neither needs the unit nor is gated.
      const macroUnitsUnresolved = Object.keys(macroUnitByCode).length === 0; // pending OR errored/empty
      const unitPending = ind.type === 'MACRO_RATE' && !macroUnit && (macroListPending || macroUnitsUnresolved);
      // An index-unit macro series (CPI/PPI, or an INDEX-unit MACRO_RATE like the TLREF Index) is a
      // cumulative level; in annual (level) mode it becomes its derived YoY rate so it shares the % axis
      // with the raw annual rates. PERCENT rates are already annual, so they stay raw in annual mode.
      const isIndexLevel = ind.type === 'MACRO_INFLATION'
        || (ind.type === 'MACRO_RATE' && macroUnit === 'INDEX');
      const compound = !levelMode && (ind.type === 'MACRO_DEPOSIT'
        || (ind.type === 'MACRO_RATE' && macroUnit === 'PERCENT'));
      let points;
      if (unitPending) {
        points = [];
      } else if (levelMode && isIndexLevel) {
        points = deriveRateSeries(raw, 'yoy');
      } else if (compound) {
        points = compoundRateSeries(raw, fillUntil);
      } else {
        points = raw;
      }
      return {
        indicator: { ...ind, displayName: displayLabel(t, ind, macroLabelByCode) },
        points,
        // Carry the actual compound decision so downstream steps (FX conversion, start-point
        // interpolation) treat a compounded growth-index (money) differently from a raw level
        // (index-unit rate / step series) — never re-derive intent from the type alone.
        compounded: compound,
        color: colorFor(ind, idx),
      };
    }),
    [selected, queries, t, macroLabelByCode, macroUnitByCode, macroListPending, levelMode, fillUntil]
  );

  const backfilledSeriesData = useMemo(
    () => rawSeriesData.map((s) => ({
      ...s,
      // Sanitize at the source: drop any null/dateless point so every downstream sort/map/findIndex over a
      // series' points (commonStartDate, convertedData) is guaranteed real objects — a single null point here
      // used to throw inside a useMemo and blank the whole compare page (white screen).
      points: backFillToWindowStart(
        (s.points || []).filter((p) => p && p.date != null),
        bounds.from,
      ) || [],
    })),
    [rawSeriesData, bounds.from]
  );

  const commonStartDate = useMemo(() => {
    if (backfilledSeriesData.length < 2) return null;
    let latest = null;
    for (const s of backfilledSeriesData) {
      if (!s.points || s.points.length === 0) continue;
      const first = [...s.points].sort((a, b) =>
        String(a.date).localeCompare(String(b.date)))[0]?.date;
      if (first && (!latest || first > latest)) latest = first;
    }
    return latest;
  }, [backfilledSeriesData]);

  const convertedData = useMemo(() => {
    // fillUntil (lifted above) is the local-zone right edge: bounds.to under explicit Beater bounds,
    // else today. Forward-fill must stop there so a past-pinned window doesn't stamp the last value
    // across every day up to today, producing a flat tail that masquerades as "no price change".
    return backfilledSeriesData.map((s) => {
      // No commonStartDate trim: every series keeps its full history (so CPI shows its pre-window data)
      // and the chart builder normalizes each series from commonStartDate's value as the shared baseline.
      // Belt-and-braces: re-drop any null/dateless point (a fill helper could in theory reintroduce one) so the
      // per-date map/findIndex below never dereferences a null and crashes the page.
      let pts = (s.points || []).filter((p) => p && p.date != null);
      const native = nativeCurrencyFor(s.indicator.type, s.indicator.code);
      const isPortfolioSeries = s.indicator.type === 'PORTFOLIO';
      if (isPortfolioSeries) {
        // The portfolio TWR index line is FX-converted per-date to the target currency exactly like the
        // asset lines (only outside originalView, where each series stays in its own native). Converting
        // the index per-date and letting the builder normalize from the shared start yields the correct
        // currency-adjusted return — idx(t)/FX(t) ÷ idx(0)/FX(0) = TRY-return × FX(0)/FX(t) — NOT a
        // distortion. Leaving it raw showed a TRY return beside USD/EUR-converted assets, a large gap
        // given TRY depreciation. For a TRY view (or originalView, or a portfolio-vs-CPI compare where
        // targetCurrency stays TRY) native == targetCurrency, so this is a no-op and the
        // portfolio-vs-inflation read remains a pure TRY real return.
        // The TL P&L overlay (pnlTry) is a concrete money figure shown in the tooltip/info-bar with the
        // targetCurrency symbol, so it converts whenever native != targetCurrency — INCLUDING originalView,
        // otherwise a TRY amount renders under a $/€ symbol.
        if (native !== targetCurrency) {
          // Once a portfolio is closed it earns 0 daily return, so its TWR index value is identical every day
          // (a flat TRY line). Converting that frozen TRY value at EACH later day's FX would manufacture an
          // FX-driven slope in USD/EUR where TRY is flat — the line appears to drift after the position is
          // closed. Lock the FX at the freeze date (the first day of the final constant-value run) so the
          // closed tail stays flat in every currency, matching the TRY view. An active portfolio's last point
          // differs from the prior one, leaving freezeDate null → per-date FX as before.
          let freezeDate = null;
          for (let i = pts.length - 1; i > 0; i -= 1) {
            if (Number(pts[i].value) !== Number(pts[i - 1].value)) break;
            freezeDate = pts[i - 1].date;
          }
          pts = pts.map((p) => {
            const fxDate = freezeDate && p.date > freezeDate ? freezeDate : p.date;
            // Prefer the backend per-currency P&L (value@point-date FX − cost@entry-date FX, closed lots at
            // exit FX). Converting the netted TRY P&L at the point rate mis-converts the cost leg (cost should
            // lock at each lot's entry-date FX), so convertBetween is only a fallback when the frame is absent.
            const framePnl = p.pnlByCcy && p.pnlByCcy[targetCurrency] != null
              ? Number(p.pnlByCcy[targetCurrency]) : null;
            const cpPnl = framePnl != null
              ? framePnl
              : (p.pnlTry != null ? convertBetween(p.pnlTry, native, targetCurrency, fxDate) : null);
            // Prefer the backend per-currency RETURN INDEX (real cost@entry-date FX return); the TRY-index
            // single-date conversion is only a fallback when that frame is absent (it collapses every lot's
            // entry FX into the window start). This is why a foreign frame now matches the portfolio page.
            const cpVal = !originalView
              ? (p.valueByCcy && p.valueByCcy[targetCurrency] != null
                  ? Number(p.valueByCcy[targetCurrency])
                  : convertBetween(p.value, native, targetCurrency, fxDate))
              : null;
            return { ...p, value: cpVal ?? p.value, pnlTry: cpPnl ?? p.pnlTry };
          });
        }
      } else if (!levelMode && !isRateLike(s.indicator.type)
          && (!isMacro(s.indicator.type) || s.compounded) && !originalView && native !== targetCurrency) {
        // FX-convert only genuine monetary values: asset prices, and compounded macro growth-indices
        // (deposits / PERCENT-unit rates). A macro series left UNCOMPOUNDED is a currency-agnostic LEVEL —
        // a raw interest-rate % OR an index-unit rate like the BIST TLREF Index (~6022) — so the
        // (!isMacro || s.compounded) clause keeps it out of FX conversion (multiplying a level by an FX
        // ratio is meaningless). !levelMode also excludes raw rate percentages. Cumulative compounded
        // series stay correctly FX-converted per-date.
        pts = pts.map((p) => {
          const converted = convertBetween(p.value, native, targetCurrency, p.date);
          return { ...p, value: converted ?? p.value };
        });
      }
      // Dense (asset) series fill to daily so the line stays continuous; SPARSE macro (CPI / deposit /
      // rate) is LEFT at its published cadence — the builder renders it with step:'end' and the tooltip
      // looks up each series' value-in-force via an as-of binary search, both of which work fine from the
      // real points. Inflating sparse macro to daily (CPI back to 2005 × several series → thousands of
      // redundant rows) was the main cause of the lag on the ALL range, with no visual benefit.
      const isSparse = isMacro(s.indicator.type);
      // Dense series only need daily-fill on SHORT ranges (few points → a clean continuous line is cheap).
      // On long ranges the raw daily candles are already plentiful AND get downsampled in the builder, so
      // filling weekends/holidays only inflates each array — and every add/delete recompute reruns this for
      // ALL series, which is the remaining lag. Skip the fill once the series is already dense.
      if (!isSparse && (pts.length || 0) <= 1200) {
        pts = forwardFillDaily(pts, commonStartDate || bounds.from, fillUntil);
      }
      // Extend the last value to the right edge so the line reaches today / the window end (no-op for dense
      // series and for compounded deposits whose daily tail already reaches fillUntil).
      pts = forwardFillTo(pts, fillUntil);
      // Guard against a fill helper handing back a non-array (empty/odd input) before the date math below.
      if (!Array.isArray(pts)) pts = [];
      // Trim to the common overlap start so the x-axis begins where ALL selected series have data (e.g. the
      // 2012 deposit start) instead of being dragged back to one long-history series' origin (CPI 2005).
      // A SPARSE compounded series has no point exactly at commonStartDate, so a bare trim made the builder
      // re-base it at its NEXT published point — dropping the interest accrued over [commonStartDate, next].
      // Plant a synthetic point AT commonStartDate carrying the value-in-force: geometrically interpolated for
      // a compounded growth index (exact, since the rate is constant between publishes), flat-carried for a
      // step/level series (CPI, level mode). Dense (daily-filled) series already own a point there → no-op.
      if (commonStartDate) {
        const idx = pts.findIndex((p) => p.date >= commonStartDate);
        if (idx > 0 && pts[idx] && pts[idx].date !== commonStartDate) {
          const p0 = pts[idx - 1];
          const p1 = pts[idx];
          // Geometric interpolation is exact ONLY for a compounded growth-index (constant rate between
          // publishes). A non-compounded level (index-unit rate like TLREF Index, or a step series) must be
          // flat-carried — so reuse the real compound flag instead of inferring it from the type.
          const compounded = s.compounded;
          let baseVal = Number(p0.value);
          const v0 = Number(p0.value);
          const v1 = Number(p1.value);
          if (compounded && v0 > 0 && v1 > 0) {
            const t0 = new Date(p0.date).getTime();
            const t1 = new Date(p1.date).getTime();
            const tc = new Date(commonStartDate).getTime();
            const frac = t1 > t0 ? (tc - t0) / (t1 - t0) : 0;
            baseVal = v0 * Math.pow(v1 / v0, frac);
          }
          pts = [{ ...p0, date: commonStartDate, value: baseVal }, ...pts.slice(idx)];
        } else {
          pts = pts.filter((p) => p.date >= commonStartDate);
        }
      }
      return { ...s, points: pts };
    });
  }, [backfilledSeriesData, commonStartDate, originalView, targetCurrency, convertBetween, bounds.from, fillUntil, levelMode]);

  const seriesData = convertedData;

  // The chart / info-bar baseline is the SELECTED range start (bounds.from), not the earliest fetched point.
  // Macro/deposit series are fetched ~18 months wide (to know the rate in force at the window start), and for a
  // single series commonStartDate is null — so without this clamp a deposit anchored ~18mo early and its line
  // started at the carried-over accumulated value (rebased to ~125k) instead of 100k AT the range start. When a
  // series' own history begins AFTER bounds.from (shorter history), commonStartDate wins — we can't anchor
  // before a series exists.
  const baselineDate = useMemo(
    () => (commonStartDate && bounds.from && commonStartDate > bounds.from ? commonStartDate : bounds.from),
    [commonStartDate, bounds.from],
  );

  // Always normalize to % when the portfolio is in play (it's a return series, comparable to inflation),
  // and whenever more than one series is being compared.
  const normalize = !levelMode && (selected.length > 1 || selected.some((s) => s.type === 'PORTFOLIO'));


  // Shared 0% anchor for the whole compare: commonStartDate/baselineDate advanced past any leading split-like
  // cliff (one price series' launch crash advances the baseline for ALL series). Computed ONCE here and passed
  // to both buildOption and CompareInfoBar so the headline % is taken at the exact date the chart line rebases
  // from — without this, the info-bar recomputed a per-series baseline and desynced from the plotted line.
  const sharedBaselineDate = useMemo(
    () => computeSharedBaselineDate(seriesData, baselineDate, levelMode),
    [seriesData, baselineDate, levelMode]
  );

  const option = useMemo(
    () => buildOption(seriesData, normalize, isDark, targetCurrency, baselineDate, levelMode, sharedBaselineDate),
    [seriesData, normalize, isDark, targetCurrency, baselineDate, levelMode, sharedBaselineDate]
  );

  return {
    range,
    bounds,
    isLoading,
    homogeneousRates,
    valueMode,
    setValueMode,
    levelMode,
    seriesData,
    normalize,
    sharedBaselineDate,
    option,
  };
}
