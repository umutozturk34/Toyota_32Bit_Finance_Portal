import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import useNavigationBack from '../../../shared/hooks/useNavigationBack';
import { motion } from 'framer-motion';
import useSessionState from '../../../shared/hooks/useSessionState';
import Card from '../../../shared/components/card';
import { usePortfolioList } from '../../portfolio/hooks/usePortfolioData';
import { useMacroIndicators } from '../../macro/hooks/useMacroIndicators';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { useTheme } from '../../../shared/context/useTheme';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import useChartRange from '../../../shared/hooks/useChartRange';
import { isMacro, parseInitialSelection } from '../lib/compareSeriesUtils';
import { buildBackTarget } from '../lib/compareNav';
import { MAX_COMPARE, MODES } from '../lib/compareConstants';
import useCompareFrame from '../hooks/useCompareFrame';
import useCompareSeries from '../hooks/useCompareSeries';
import CompareHeader from '../components/compare/CompareHeader';
import CompareModeNav from '../components/compare/CompareModeNav';
import CompareChipBar from '../components/compare/CompareChipBar';
import ComparePickerRow from '../components/compare/ComparePickerRow';
import CompareRangeControls from '../components/compare/CompareRangeControls';
import CompareNotices from '../components/compare/CompareNotices';
import CompareChartArea from '../components/compare/CompareChartArea';

// FX history (and therefore every convertible series) starts at the 2000-01-04 floor; the custom-range
// picker cannot go earlier or the per-date conversion has no rate to anchor to.
const FX_FLOOR_DATE = '2000-01-04';

export default function ComparePage() {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { currency: displayCurrency } = useMoney();
  const { convertBetween } = useRateHistory();
  const [params, setParams] = useSearchParams();
  const { data: userPortfolios } = usePortfolioList();
  // code -> i18n label key for every macro indicator, so a macro selected in compare (which only carries
  // {type,code,name}) can resolve its localized name via marketOverview.macro.<label>. isPending gates the
  // cumulative classification on a URL-reload race: a macro series whose unit wasn't carried in the link
  // must not be classified (compound vs raw) before this list resolves, or a PERCENT rate could briefly
  // render as a raw level-ratio.
  const { data: macroList, isPending: macroListPending, isError: macroListError } = useMacroIndicators();
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

  // A macro series added before the macro list resolved — or restored from an older URL without a `units`
  // param — carries no unit. Once the list loads, backfill the unit onto each such macro selection so the
  // cumulative classification (compound a PERCENT rate vs keep an INDEX level raw) becomes reliable from the
  // selection itself and the unit is persisted back into the URL. Idempotent: only macro series missing a unit.
  useEffect(() => {
    if (Object.keys(macroUnitByCode).length === 0) return;
    setSelected((prev) => {
      let changed = false;
      const next = prev.map((s) => {
        if (!isMacro(s.type) || s.unit) return s;
        const unit = macroUnitByCode[s.code];
        if (!unit) return s;
        changed = true;
        return { ...s, unit };
      });
      return changed ? next : prev;
    });
  }, [macroUnitByCode, setSelected]);

  const [rangeId, setRangeId] = useChartRange();
  const initialRangeRef = useRef(params.get('range'));
  const initialStartRef = useRef(params.get('start'));
  const initialEndRef = useRef(params.get('end'));
  const initialCurrencyRef = useRef(params.get('currency'));
  const initialNominalsRef = useRef(params.get('nominals'));
  const [useExplicitBounds, setUseExplicitBounds] = useState(
    !!(initialStartRef.current && initialEndRef.current),
  );
  // User-editable custom date range (the "Özel" range). Seeded from a shared explicit-bounds link's
  // start/end so such a link prefills the pickers; otherwise filled when the user opens the custom range.
  const [customFrom, setCustomFrom] = useState(initialStartRef.current || '');
  const [customTo, setCustomTo] = useState(initialEndRef.current || '');

  const {
    authoritativeReturns,
    forceTryFrame,
    targetCurrency,
    originalView,
    currencyReconciledNotice,
    macroUnitLoadFailed,
  } = useCompareFrame({
    selected,
    displayCurrency,
    useExplicitBounds,
    initialNominals: initialNominalsRef.current,
    initialCurrency: initialCurrencyRef.current,
    macroListError,
    macroUnitByCode,
  });

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

  useEffect(() => {
    const next = new URLSearchParams(params);
    next.set('tab', 'compare');
    if (selected.length > 0) {
      next.set('codes', selected.map((s) => s.code).join(','));
      next.set('types', selected.map((s) => s.type).join(','));
      // Round-trip each macro series' unit positionally (empty slot for non-macro / unknown) so a reload or
      // shared link keeps the cumulative classification without re-waiting on the async macro list. Only
      // emitted when at least one unit is known, otherwise the param is dropped (older-link compatible).
      const units = selected.map((s) => (isMacro(s.type) && s.unit ? s.unit : ''));
      if (units.some(Boolean)) next.set('units', units.join(','));
      else next.delete('units');
    } else {
      next.delete('codes');
      next.delete('types');
      next.delete('units');
    }
    if (mode !== 'assets') next.set('mode', mode);
    else next.delete('mode');
    if (rangeId) next.set('range', rangeId);
    if (useExplicitBounds) {
      if (customFrom) next.set('start', customFrom); else next.delete('start');
      if (customTo) next.set('end', customTo); else next.delete('end');
    } else {
      next.delete('start');
      next.delete('end');
      next.delete('currency');
      next.delete('nominals');
    }
    setParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- params/setParams omitted on purpose: setParams is unstable in react-router v7, adding it self-triggers an infinite URL-write loop
  }, [selected, mode, rangeId, useExplicitBounds, customFrom, customTo]);

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

  const {
    range,
    isLoading,
    homogeneousRates,
    valueMode,
    setValueMode,
    levelMode,
    seriesData,
    normalize,
    sharedBaselineDate,
    option,
  } = useCompareSeries({
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
    authoritativeReturns,
  });

  function addAsset(asset) {
    if (selected.length >= MAX_COMPARE) return;
    if (selected.some((s) => s.code === asset.code && s.type === asset.type)) return;
    const entry = {
      type: asset.type,
      code: asset.code,
      name: asset.name || asset.code,
    };
    // Carry the macro indicator's unit (PERCENT vs INDEX) onto the stored selection so the cumulative
    // classification (compound a PERCENT rate, keep an INDEX level raw) is reliable from the selection
    // itself — never dependent on the separate async macroUnitByCode map resolving in time. The unified
    // search result doesn't include unit, so resolve it from the macro list (asset.unit honored if a
    // future search payload starts carrying it).
    if (isMacro(asset.type)) {
      const unit = asset.unit ?? macroUnitByCode[asset.code];
      if (unit) entry.unit = unit;
    }
    setSelected([...selected, entry]);
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
      <CompareHeader backTarget={backTarget} goBack={goBack} cameFrom={cameFrom} t={t} />

      <CompareModeNav mode={mode} switchMode={switchMode} t={t} />

      <Card variant="elevated" radius="xl" padding="lg" backdropBlur className="space-y-4">
        <CompareChipBar seriesData={seriesData} selected={selected} removeAsset={removeAsset} t={t} />

        <ComparePickerRow
          selected={selected}
          addAsset={addAsset}
          modeDef={modeDef}
          mode={mode}
          userPortfolios={userPortfolios}
          portfolioPickerRef={portfolioPickerRef}
          portfolioPickerOpen={portfolioPickerOpen}
          setPortfolioPickerOpen={setPortfolioPickerOpen}
          addPortfolio={addPortfolio}
          t={t}
        />

        <CompareRangeControls
          rangeId={rangeId}
          setRangeId={setRangeId}
          range={range}
          useExplicitBounds={useExplicitBounds}
          setUseExplicitBounds={setUseExplicitBounds}
          customFrom={customFrom}
          setCustomFrom={setCustomFrom}
          customTo={customTo}
          setCustomTo={setCustomTo}
          fxFloorDate={FX_FLOOR_DATE}
          homogeneousRates={homogeneousRates}
          valueMode={valueMode}
          setValueMode={setValueMode}
          t={t}
        />

        <CompareNotices
          levelMode={levelMode}
          normalize={normalize}
          selected={selected}
          currencyReconciledNotice={currencyReconciledNotice}
          forceTryFrame={forceTryFrame}
          targetCurrency={targetCurrency}
          macroUnitLoadFailed={macroUnitLoadFailed}
          t={t}
        />

        <CompareChartArea
          isLoading={isLoading}
          seriesData={seriesData}
          option={option}
          selected={selected}
          targetCurrency={targetCurrency}
          sharedBaselineDate={sharedBaselineDate}
          authoritativeReturns={authoritativeReturns}
          t={t}
        />
      </Card>
    </motion.div>
  );
}
