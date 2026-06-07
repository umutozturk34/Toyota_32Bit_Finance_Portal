import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import useNavigationBack from '../../../shared/hooks/useNavigationBack';
import { useQueries } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Info, ArrowLeft, Search, LineChart, Briefcase, ChevronDown, GitCompare } from 'lucide-react';
import useSessionState from '../../../shared/hooks/useSessionState';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
import { usePortfolioList } from '../../portfolio/hooks/usePortfolioData';
import { useMacroIndicators } from '../../macro/hooks/useMacroIndicators';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { useTheme } from '../../../shared/context/useTheme';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import useChartRange from '../../../shared/hooks/useChartRange';
import { RANGES } from '../../macro/constants';
import CompareInfoBar from '../components/CompareInfoBar';
import { buildOption } from '../lib/compareChartBuilder';
import {
  isMacro,
  isRateLike,
  rangeBoundsCalendar,
  fetchSeries,
  colorFor,
  parseInitialSelection,
  forwardFillTo,
  forwardFillDaily,
  backFillToWindowStart,
  displayLabel,
  nativeCurrencyFor,
  compoundRateSeries,
} from '../lib/compareSeriesUtils';
import { buildBackTarget } from '../lib/compareNav';
import { MAX_COMPARE, MODES } from '../lib/compareConstants';

export default function ComparePage() {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { currency: displayCurrency } = useMoney();
  const { convertBetween } = useRateHistory();
  const [params, setParams] = useSearchParams();
  const { data: userPortfolios } = usePortfolioList();
  // code -> i18n label key for every macro indicator, so a macro selected in compare (which only carries
  // {type,code,name}) can resolve its localized name via marketOverview.macro.<label>.
  const { data: macroList } = useMacroIndicators();
  const macroLabelByCode = useMemo(
    () => Object.fromEntries((macroList || []).map((m) => [m.code, m.label]).filter(([, l]) => l)),
    [macroList],
  );
  const macroUnitByCode = useMemo(
    () => Object.fromEntries((macroList || []).map((m) => [m.code, m.unit]).filter(([, u]) => u)),
    [macroList],
  );
  const [portfolioPickerOpen, setPortfolioPickerOpen] = useState(false);
  const portfolioPickerRef = useRef(null);
  const cameFrom = params.get('from');
  const backTarget = useMemo(
    () => buildBackTarget(cameFrom, params.get('fromType'), params.get('fromCode')),
    [cameFrom, params],
  );
  // Prefer real browser-back so returns → detail → compare → back → detail → back → returns walks history
  // linearly. The old navigate(backTarget) PUSHED a reconstructed detail route, so detail's own back (which
  // uses navigate(-1)) landed on the compare entry just below it — an endless detail ↔ compare ping-pong.
  // backTarget stays the fallback for direct-link arrivals that have no in-app history to step back through.
  const goBack = useNavigationBack(backTarget);
  const [mode, setMode] = useSessionState('compare:mode', params.get('mode') || 'assets');
  const [selected, setSelected] = useSessionState('compare:selected', parseInitialSelection(params));

  // Items restored from URL params arrive with name === code (parseInitialSelection cannot
  // resolve portfolio names from an id alone); once the portfolio list loads we backfill the
  // real names so chips and the chart legend display "Demo Portföy" instead of "3".
  useEffect(() => {
    if (!userPortfolios || userPortfolios.length === 0) return;
    setSelected((prev) => {
      let changed = false;
      const next = prev.map((s) => {
        if (s.type !== 'PORTFOLIO') return s;
        if (s.name && s.name !== s.code) return s;
        const p = userPortfolios.find((x) => String(x.id) === String(s.code));
        if (!p || !p.name) return s;
        changed = true;
        return { ...s, name: p.name };
      });
      return changed ? next : prev;
    });
  }, [userPortfolios, setSelected]);

  // URL/beater hand-offs arrive with name === code for market assets (the link only carries codes), so the
  // legend/info-bar/chips would show "PHN" instead of "NEO PORTFÖY İKİNCİ SERBEST FON". Backfill the real
  // long name from the market API. Commodities (i18n) and macros (label map) and the id-only portfolio
  // resolve their names elsewhere and are skipped; each (type,code) is fetched at most once.
  const enrichedAssetNamesRef = useRef(new Set());
  useEffect(() => {
    const needs = selected.filter((s) =>
      s.type !== 'PORTFOLIO' && s.type !== 'COMMODITY' && !isMacro(s.type)
      && (!s.name || s.name === s.code)
      && !enrichedAssetNamesRef.current.has(`${s.type}:${s.code}`));
    if (needs.length === 0) return undefined;
    let cancelled = false;
    needs.forEach((s) => enrichedAssetNamesRef.current.add(`${s.type}:${s.code}`));
    (async () => {
      const resolved = await Promise.all(needs.map(async (s) => {
        try {
          const asset = await unifiedMarketService.getByCode(s.type, s.code);
          return asset?.name && asset.name !== s.code ? { key: `${s.type}:${s.code}`, name: asset.name } : null;
        } catch {
          return null;
        }
      }));
      if (cancelled) return;
      const byKey = new Map(resolved.filter(Boolean).map((r) => [r.key, r.name]));
      if (byKey.size === 0) return;
      setSelected((prev) => {
        let changed = false;
        const next = prev.map((s) => {
          const nm = byKey.get(`${s.type}:${s.code}`);
          if (nm && (!s.name || s.name === s.code)) { changed = true; return { ...s, name: nm }; }
          return s;
        });
        return changed ? next : prev;
      });
    })();
    return () => { cancelled = true; };
  }, [selected, setSelected]);
  const [rangeId, setRangeId] = useChartRange();
  const initialRangeRef = useRef(params.get('range'));
  const initialStartRef = useRef(params.get('start'));
  const initialEndRef = useRef(params.get('end'));
  const initialCurrencyRef = useRef(params.get('currency'));
  const initialNominalsRef = useRef(params.get('nominals'));
  const [useExplicitBounds, setUseExplicitBounds] = useState(
    !!(initialStartRef.current && initialEndRef.current),
  );
  // Beater click-through carries the table's authoritative (cached, backend-computed) nominal returns as
  // code:pct pairs, so CompareInfoBar prints the exact same % as the row clicked instead of the frontend
  // re-compound (which drifts ~0.5pt on the lead-in). Only while the pinned Beater window is active; once
  // the user changes range/selection (useExplicitBounds off) the recomputed % takes back over.
  const authoritativeReturns = useMemo(() => {
    if (!useExplicitBounds || !initialNominalsRef.current) return null;
    const map = {};
    for (const pair of initialNominalsRef.current.split(',')) {
      const sep = pair.lastIndexOf(':');
      if (sep < 0) continue;
      const code = pair.slice(0, sep);
      const n = Number(pair.slice(sep + 1));
      if (code && Number.isFinite(n)) map[code] = n;
    }
    return Object.keys(map).length > 0 ? map : null;
  }, [useExplicitBounds]);
  // Comparing against a USD/EUR deposit frames the whole chart in that deposit's currency
  // (single non-TRY deposit only; mixed/none → no override).
  const depositFrameCurrency = useMemo(() => {
    const set = new Set(
      selected
        .filter((s) => s.type === 'MACRO_DEPOSIT')
        .map((s) => nativeCurrencyFor(s.type, s.code))
        .filter((c) => c !== 'TRY'),
    );
    return set.size === 1 ? [...set][0] : null;
  }, [selected]);
  // A TR-only inflation index (CPI/TÜFE) — and the portfolio-vs-any-TR-indexed case — forces the WHOLE
  // chart into TRY. CPI lives only in TRY (here +1800%), so a EUR/USD deposit left in its own currency
  // (a few % growth) looks crushed by inflation, when in TRY terms (its FX-converted growth = native
  // interest + TRY depreciation, ~+2500%) it actually BEAT it. Pinning to TRY converts every non-TRY
  // series per-date so "did it beat inflation" is a real apples-to-apples read. An explicit URL currency
  // or a single non-TRY deposit frame still wins over this.
  const forceTryFrame = useMemo(() => {
    if (initialCurrencyRef.current) return false;
    const hasInflation = selected.some((s) => s.type === 'MACRO_INFLATION');
    const hasPortfolio = selected.some((s) => s.type === 'PORTFOLIO');
    const hasTrIndexed = selected.some((s) => isRateLike(s.type));
    return hasInflation || (hasTrIndexed && hasPortfolio);
  }, [selected]);
  // Mixed native currencies among the compared series (e.g. a USD-native crypto vs a TRY stock; portfolio
  // counts as TRY). There is no shared native to read returns in, so they must NOT stay in "original" view —
  // a USD asset left in USD would plot its USD-local return on a TRY chart, hiding the USD/TRY move and making
  // the compare unfair. When mixed, frame the whole chart in TRY and convert every series per-date.
  const mixedNative = useMemo(() => {
    const set = new Set(
      selected
        .filter((s) => !isMacro(s.type))
        .map((s) => (s.type === 'PORTFOLIO' ? 'TRY' : nativeCurrencyFor(s.type, s.code))),
    );
    return set.size > 1;
  }, [selected]);
  const targetCurrency = useMemo(() => {
    if (initialCurrencyRef.current) return initialCurrencyRef.current;
    // CPI/inflation framing (TRY) wins over a single non-TRY deposit frame too: a EUR deposit vs CPI is
    // only meaningful in TRY (deposit's TRY-terms growth vs TRY inflation), not in EUR vs a TRY index.
    if (forceTryFrame) return 'TRY';
    // An explicit display-currency choice (TRY/USD/EUR) wins over the deposit-native frame: a EUR deposit
    // with TRY selected converts per-date and reads in TRY. Only "Original" falls back to the deposit's own
    // currency — foreign deposits are always convertible, so there is no need to force their native frame.
    if (displayCurrency !== 'ORIGINAL') return displayCurrency;
    if (depositFrameCurrency) return depositFrameCurrency;
    // Mixed natives → TRY (the base): every series converts to TRY per-date for a fair "in my money" compare.
    if (mixedNative) return 'TRY';
    const first = selected.find((s) => !isMacro(s.type) && s.type !== 'PORTFOLIO');
    return first ? nativeCurrencyFor(first.type, first.code) : 'TRY';
  }, [displayCurrency, selected, depositFrameCurrency, forceTryFrame, mixedNative]);
  // "Original" view (each series in its own native) only when no explicit currency / non-TRY deposit frame
  // / forced-TRY inflation frame is in effect AND every series shares one native (nothing to reconcile).
  const originalView = displayCurrency === 'ORIGINAL'
    && !initialCurrencyRef.current
    && !depositFrameCurrency
    && !forceTryFrame
    && !mixedNative;

  useEffect(() => {
    if (initialRangeRef.current) {
      setRangeId(initialRangeRef.current);
    }
    const fromUrl = parseInitialSelection(params);
    if (fromUrl.length > 0) {
      const sameAsCurrent = fromUrl.length === selected.length
        && fromUrl.every((u, i) => selected[i] && u.code === selected[i].code && u.type === selected[i].type);
      if (!sameAsCurrent) setSelected(fromUrl);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-once URL->state hydration; re-running would clobber the user's later selection/range edits
  }, []);

  const rangeUserChangedRef = useRef(false);
  useEffect(() => {
    if (!rangeUserChangedRef.current) {
      if (rangeId === initialRangeRef.current) {
        rangeUserChangedRef.current = true;
      }
      return;
    }
    if (useExplicitBounds && rangeId && rangeId !== initialRangeRef.current) {
      setUseExplicitBounds(false);
    }
  }, [rangeId, useExplicitBounds]);

  useEffect(() => {
    const next = new URLSearchParams(params);
    next.set('tab', 'compare');
    if (selected.length > 0) {
      next.set('codes', selected.map((s) => s.code).join(','));
      next.set('types', selected.map((s) => s.type).join(','));
    } else {
      next.delete('codes');
      next.delete('types');
    }
    if (mode !== 'assets') next.set('mode', mode);
    else next.delete('mode');
    if (rangeId) next.set('range', rangeId);
    if (!useExplicitBounds) {
      next.delete('start');
      next.delete('end');
      next.delete('currency');
      next.delete('nominals');
    }
    setParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- params/setParams omitted on purpose: setParams is unstable in react-router v7, adding it self-triggers an infinite URL-write loop
  }, [selected, mode, rangeId, useExplicitBounds]);

  useEffect(() => {
    if (selected.some((s) => isMacro(s.type) || s.type === 'PORTFOLIO') && mode === 'assets') {
      setMode('mixed');
    }
  }, [selected, mode, setMode]);

  useEffect(() => {
    if (!portfolioPickerOpen) return undefined;
    function handler(e) {
      if (portfolioPickerRef.current && !portfolioPickerRef.current.contains(e.target)) {
        setPortfolioPickerOpen(false);
      }
    }
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [portfolioPickerOpen]);

  function addPortfolio(p) {
    if (selected.length >= MAX_COMPARE) return;
    const code = String(p.id);
    if (selected.some((s) => s.code === code && s.type === 'PORTFOLIO')) return;
    setSelected([...selected, { type: 'PORTFOLIO', code, name: p.name }]);
    setPortfolioPickerOpen(false);
  }

  const modeDef = MODES.find((m) => m.id === mode);
  const range = useMemo(() => RANGES.find((r) => r.id === rangeId) || RANGES[3], [rangeId]);
  const bounds = useMemo(() => {
    if (useExplicitBounds && initialStartRef.current && initialEndRef.current) {
      return { from: initialStartRef.current, to: initialEndRef.current };
    }
    return rangeBoundsCalendar(range.id);
  }, [range, useExplicitBounds]);

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
  // Deposits are ALWAYS interest rates (PERCENT) — treat MACRO_DEPOSIT as eligible directly rather than
  // looking it up in macroUnitByCode (the macro-indicator list the map is built from may not include every
  // deposit tenor, which otherwise hid the level/cumulative toggle for a deposit-vs-deposit compare). Only
  // MACRO_RATE needs the unit check, since it can be a PERCENT rate (toggle) or an INDEX level (no toggle).
  const homogeneousRates = useMemo(
    () => selected.length >= 2 && selected.every((s) =>
      s.type === 'MACRO_DEPOSIT'
      || (s.type === 'MACRO_RATE' && macroUnitByCode[s.code] === 'PERCENT')),
    [selected, macroUnitByCode],
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
      const macroUnit = macroUnitByCode[ind.code];
      const compound = !levelMode && (ind.type === 'MACRO_DEPOSIT'
        || (ind.type === 'MACRO_RATE' && macroUnit === 'PERCENT'));
      const points = compound ? compoundRateSeries(raw, fillUntil) : raw;
      return {
        indicator: { ...ind, displayName: displayLabel(t, ind, macroLabelByCode) },
        points,
        color: colorFor(ind, idx),
      };
    }),
    [selected, queries, t, macroLabelByCode, macroUnitByCode, levelMode, fillUntil]
  );

  const backfilledSeriesData = useMemo(
    () => rawSeriesData.map((s) => ({
      ...s,
      points: backFillToWindowStart(s.points || [], bounds.from),
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
      let pts = s.points || [];
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
            const cpVal = !originalView ? convertBetween(p.value, native, targetCurrency, fxDate) : null;
            return { ...p, value: cpVal ?? p.value, pnlTry: cpPnl ?? p.pnlTry };
          });
        }
      } else if (!levelMode && !isRateLike(s.indicator.type) && !originalView && native !== targetCurrency) {
        // !levelMode guard: in LEVEL mode the plotted value is the raw annual interest-rate PERCENTAGE
        // (compound is off), and a rate level is currency-agnostic — FX-converting it would render a 50%
        // TRY deposit rate as ~1.5% beside an unconverted USD deposit. Cumulative mode is unaffected: there
        // the value is the compounded growth-index (money), which is correctly FX-converted per-date.
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
          const compounded = !levelMode && isMacro(s.indicator.type) && !isRateLike(s.indicator.type);
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


  const option = useMemo(
    () => buildOption(seriesData, normalize, isDark, targetCurrency, baselineDate, levelMode),
    [seriesData, normalize, isDark, targetCurrency, baselineDate, levelMode]
  );

  function addAsset(asset) {
    if (selected.length >= MAX_COMPARE) return;
    if (selected.some((s) => s.code === asset.code && s.type === asset.type)) return;
    setSelected([...selected, {
      type: asset.type,
      code: asset.code,
      name: asset.name || asset.code,
    }]);
  }

  function removeAsset(code, type) {
    setSelected(selected.filter((s) => !(s.code === code && s.type === type)));
  }

  function switchMode(newMode) {
    if (newMode === mode) return;
    setMode(newMode);
    if (newMode === 'assets') {
      setSelected(selected.filter((s) => !isMacro(s.type) && s.type !== 'PORTFOLIO'));
    }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="space-y-5"
    >
      <header className="pb-3 border-b border-border-default/40">
        {backTarget && (
          <motion.button
            type="button"
            onClick={goBack}
            whileHover={{ x: -2 }}
            whileTap={{ scale: 0.97 }}
            transition={{ type: 'spring', stiffness: 400, damping: 28 }}
            className="group inline-flex items-center gap-2 mb-4 rounded-full border border-border-default/80 bg-bg-elevated/80 backdrop-blur-md pl-1.5 pr-4 py-1.5 text-fg-muted hover:text-accent hover:border-accent/50 hover:bg-accent/10 transition-colors cursor-pointer"
            style={{ boxShadow: '0 2px 12px -4px rgba(0,0,0,0.3)' }}
          >
            <span className="flex items-center justify-center w-7 h-7 rounded-full bg-bg-base/70 border border-border-default/60 group-hover:bg-accent/15 group-hover:border-accent/50 group-hover:shadow-[0_0_10px_-2px_rgba(99,102,241,0.5)] transition-all">
              <ArrowLeft className="h-3.5 w-3.5 transition-transform group-hover:-translate-x-0.5" />
            </span>
            <span className="font-display text-sm font-semibold tracking-tight">
              {t(`analytics.backTo.${cameFrom}`, { defaultValue: 'Geri dön' })}
            </span>
          </motion.button>
        )}
        <div className="flex items-center gap-2.5">
          <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/12 text-accent shrink-0">
            <GitCompare className="h-5 w-5" />
          </span>
          <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg tracking-tight leading-none">
            {t('analytics.compareTitle', { defaultValue: 'Karşılaştırma' })}
          </h1>
        </div>
        <p className="mt-2 text-sm text-fg-muted max-w-2xl">
          {t('analytics.compareSubtitle', {
            defaultValue: 'Farklı varlıkları aynı grafikte yan yana getir. Tutar/tarih gerekmez — sadece geçmiş fiyat hareketleri.',
          })}
        </p>
      </header>

      <nav className="flex items-center gap-1 flex-wrap">
        {MODES.map(({ id, labelKey, Icon }) => {
          const active = mode === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => switchMode(id)}
              className={`relative flex items-center gap-2 px-3 sm:px-4 py-2 text-sm font-semibold rounded-lg cursor-pointer border-none transition-colors whitespace-nowrap ${
                active ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
              }`}
            >
              <Icon className="h-4 w-4" />
              {t(`analytics.${labelKey}`, { defaultValue: id })}
            </button>
          );
        })}
      </nav>

      <Card variant="elevated" radius="xl" padding="lg" backdropBlur className="space-y-4">
        <div className="flex items-center gap-1.5 flex-wrap min-h-[28px]">
          <AnimatePresence>
            {seriesData.map(({ indicator: ind, color }) => (
              <motion.span
                key={`${ind.type}-${ind.code}`}
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.9 }}
                className="inline-flex items-center gap-1.5 rounded-md pl-2 pr-1 py-1 text-xs font-mono"
                style={{ background: `${color}14`, boxShadow: `inset 0 0 0 1px ${color}40` }}
              >
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: color }} />
                <span className="text-fg font-semibold tracking-tight text-[11px]">{ind.displayName}</span>
                <span className="text-fg-subtle">·</span>
                <span className="text-fg-muted uppercase tracking-[0.12em] text-[10px]">{t(`assets.labels.${ind.type}`, { defaultValue: ind.type })}</span>
                <button
                  type="button"
                  onClick={() => removeAsset(ind.code, ind.type)}
                  className="ml-0.5 h-4 w-4 flex items-center justify-center rounded-sm text-fg-subtle hover:text-fg hover:bg-bg-elevated cursor-pointer border-none bg-transparent"
                >
                  <X className="h-2.5 w-2.5" />
                </button>
              </motion.span>
            ))}
          </AnimatePresence>
          {selected.length === 0 && (
            <span className="text-xs text-fg-subtle font-mono italic px-1 py-1">
              {t('analytics.compareEmpty', { defaultValue: 'Karşılaştırmak için aşağıdan ara ve seç.' })}
            </span>
          )}
        </div>

        {selected.length < MAX_COMPARE && (
          <div className="flex flex-col sm:flex-row items-stretch gap-2">
            <div className="flex-1 min-w-0">
              <SearchSuggestions
                onSelect={addAsset}
                navigateOnSelect={false}
                excludeCodes={selected.map((s) => s.code)}
                excludeTypes={['BOND']}
                filterType={modeDef.filterType}
                placeholder={mode === 'assets'
                  ? t('analytics.compareSearchAssets', { defaultValue: 'Hisse, kripto, fon, döviz, emtia ara…' })
                  : t('analytics.compareSearchMixed', { defaultValue: 'Asset veya makro indikatör ara…' })}
              />
            </div>
            {(userPortfolios?.length ?? 0) > 0 && (
              <div ref={portfolioPickerRef} className="relative">
                <button
                  type="button"
                  onClick={() => setPortfolioPickerOpen((v) => !v)}
                  className="w-full sm:w-auto h-full inline-flex items-center justify-center gap-1.5 rounded-md border border-border-default bg-bg-elevated hover:bg-accent/8 hover:border-accent/40 px-3 py-2 text-xs font-mono font-semibold text-fg-muted hover:text-accent transition-colors cursor-pointer"
                  title={t('analytics.comparePortfolioCta', { defaultValue: 'Portföyünü ekle' })}
                >
                  <Briefcase className="h-3.5 w-3.5" />
                  {t('analytics.comparePortfolioCta', { defaultValue: 'Portföyünü ekle' })}
                  <ChevronDown className={`h-3 w-3 transition-transform ${portfolioPickerOpen ? 'rotate-180' : ''}`} />
                </button>
                <AnimatePresence>
                  {portfolioPickerOpen && (
                    <motion.div
                      initial={{ opacity: 0, y: -4, scale: 0.98 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: -4, scale: 0.98 }}
                      transition={{ duration: 0.14 }}
                      className="absolute z-30 right-0 mt-1 w-56 max-w-[calc(100vw-2rem)] rounded-lg border border-border-default bg-bg-elevated shadow-lg overflow-hidden"
                    >
                      <div className="px-3 py-1.5 border-b border-border-default/60 text-[10px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
                        {t('analytics.portfolioPickerHeading', { defaultValue: 'Portföylerim' })}
                      </div>
                      {userPortfolios.map((p) => {
                        const code = String(p.id);
                        const alreadyAdded = selected.some((s) => s.code === code && s.type === 'PORTFOLIO');
                        return (
                          <button
                            key={p.id}
                            type="button"
                            disabled={alreadyAdded}
                            onClick={() => addPortfolio(p)}
                            className={`w-full text-left px-3 py-2 text-xs flex items-center gap-2 border-none bg-transparent transition-colors ${
                              alreadyAdded
                                ? 'text-fg-subtle cursor-not-allowed'
                                : 'text-fg hover:bg-accent/10 hover:text-accent cursor-pointer'
                            }`}
                          >
                            <Briefcase className="h-3 w-3 shrink-0" />
                            <span className="flex-1 truncate">{p.name}</span>
                            {alreadyAdded && <span className="text-[9px] font-mono text-fg-subtle">✓</span>}
                          </button>
                        );
                      })}
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            )}
          </div>
        )}

        <div className="flex flex-wrap items-center gap-1 pt-1">
          {RANGES.map((r) => (
            <button
              key={r.id}
              type="button"
              onClick={() => setRangeId(r.id)}
              className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
                rangeId === r.id
                  ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
                  : 'text-fg-muted hover:text-fg'
              }`}
            >
              {t(`marketOverview.macro.${r.labelKey}`, { defaultValue: r.id })}
            </button>
          ))}
        </div>

        {homogeneousRates && (
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setValueMode('level')}
              className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
                valueMode === 'level'
                  ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
                  : 'text-fg-muted hover:text-fg'
              }`}
            >
              {t('analytics.compareLevel', { defaultValue: 'Seviye' })}
            </button>
            <button
              type="button"
              onClick={() => setValueMode('cumulative')}
              className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
                valueMode === 'cumulative'
                  ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
                  : 'text-fg-muted hover:text-fg'
              }`}
            >
              {t('analytics.compareCumulative', { defaultValue: 'Kümülatif' })}
            </button>
          </div>
        )}

        {levelMode && (
          <div className="flex items-center gap-2 text-[10px] font-mono text-fg-subtle italic">
            <Info className="h-3 w-3" />
            {t('analytics.levelHintCompare', {
              defaultValue: 'Oran seviyesi gösteriliyor — kümülatif büyüme için "Kümülatif"e geç',
            })}
          </div>
        )}

        {normalize && (
          <div className="flex items-center gap-2 text-[10px] font-mono text-amber-500 italic">
            <Info className="h-3 w-3" />
            {t('analytics.normalizedHintCompare', {
              defaultValue: 'Her seri başlangıçta 100.000\'e endekslendi',
            })}
          </div>
        )}
        {selected.some((s) => s.type === 'PORTFOLIO') && (
          <div className="flex items-center gap-2 text-[10px] font-mono text-fg-subtle italic">
            <Info className="h-3 w-3" />
            {t('analytics.portfolioSeriesHint', {
              defaultValue: 'Portföy serisi zaman-ağırlıklı getiridir (nakit giriş/çıkışlarından bağımsız)',
            })}
          </div>
        )}

        <div className="relative rounded-xl border border-border-default/60 bg-bg-base/40 overflow-hidden h-[280px] sm:h-[380px] lg:h-[460px]">
          {isLoading && (
            <div className="absolute inset-0 flex items-center justify-center">
              <Spinner size="md" tone="accent" />
            </div>
          )}
          {!isLoading && seriesData.some((s) => s.points.length > 0) && (
            <ReactECharts option={option} style={{ height: '100%', width: '100%' }} opts={{ renderer: 'canvas' }} notMerge lazyUpdate />
          )}
          {!isLoading && (seriesData.length === 0 || seriesData.every((s) => s.points.length === 0)) && (
            <div className="absolute inset-0 flex items-center justify-center p-4">
              <EmptyState
                size="sm"
                className="border-none bg-transparent"
                icon={selected.length === 0
                  ? <Search className="h-4 w-4 text-accent" />
                  : <LineChart className="h-4 w-4 text-accent" />}
                message={selected.length === 0
                  ? t('analytics.comparePickFirst', { defaultValue: 'En az 1 enstrüman seç' })
                  : t('marketOverview.macro.noData', { defaultValue: 'Bu aralıkta veri yok' })}
              />
            </div>
          )}
        </div>

        {seriesData.length > 0 && <CompareInfoBar selected={seriesData} targetCurrency={targetCurrency} commonStartDate={baselineDate} authoritativeReturns={authoritativeReturns} t={t} />}
      </Card>
    </motion.div>
  );
}
