import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams, useLocation, useNavigate } from 'react-router-dom';
import { useQueries } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Info, BarChart3, Activity, ArrowLeft, Search, LineChart } from 'lucide-react';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
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
  rangeBounds,
  fetchSeries,
  colorFor,
  parseInitialSelection,
  forwardFillToToday,
  backFillToWindowStart,
} from '../lib/compareSeriesUtils';

const MAX_COMPARE = 6;
const MARKET_TYPES_FILTER = 'STOCK,CRYPTO,FOREX,FUND,COMMODITY,VIOP,BOND';

const MODES = [
  { id: 'assets',    labelKey: 'modeAssets',    Icon: BarChart3, filterType: MARKET_TYPES_FILTER },
  { id: 'mixed',     labelKey: 'modeMixed',     Icon: Activity,  filterType: undefined },
];

export default function ComparePage() {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { currency: displayCurrency, format: money } = useMoney();
  const { convertAt } = useRateHistory();
  const targetCurrency = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const cameFromRef = useRef(location.state?.from);
  const cameFrom = cameFromRef.current;
  const [mode, setMode] = useState(() => params.get('mode') || 'assets');
  const [selected, setSelected] = useState(() => parseInitialSelection(params));
  const [rangeId, setRangeId] = useChartRange();
  const initialRangeRef = useRef(params.get('range'));

  useEffect(() => {
    if (initialRangeRef.current) {
      setRangeId(initialRangeRef.current);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    setParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected, mode, rangeId]);

  useEffect(() => {
    if (selected.some((s) => isMacro(s.type)) && mode === 'assets') {
      setMode('mixed');
    }
  }, [selected, mode]);

  const modeDef = MODES.find((m) => m.id === mode);
  const range = useMemo(() => RANGES.find((r) => r.id === rangeId) || RANGES[3], [rangeId]);
  const bounds = useMemo(() => rangeBounds(range.days), [range]);

  const queries = useQueries({
    queries: selected.map((s) => ({
      queryKey: ['compare-page-history', s.type, s.code, bounds.from, bounds.to],
      queryFn: () => fetchSeries(s, bounds),
      enabled: !!s.code,
      staleTime: 5 * 60 * 1000,
    })),
  });
  const isLoading = queries.some((q) => q.isLoading);

  const rawSeriesData = useMemo(
    () => selected.map((ind, idx) => ({
      indicator: ind,
      points: queries[idx]?.data || [],
      color: colorFor(ind, idx),
    })),
    [selected, queries]
  );

  const commonStartDate = useMemo(() => {
    if (rawSeriesData.length < 2) return null;
    let latest = null;
    for (const s of rawSeriesData) {
      if (!s.points || s.points.length === 0) continue;
      const first = [...s.points].sort((a, b) =>
        String(a.date).localeCompare(String(b.date)))[0]?.date;
      if (first && (!latest || first > latest)) latest = first;
    }
    return latest;
  }, [rawSeriesData]);

  const convertedData = useMemo(() => {
    return rawSeriesData.map((s) => {
      let pts = s.points || [];
      pts = backFillToWindowStart(pts, bounds.from);
      if (commonStartDate) {
        pts = pts.filter((p) => p.date >= commonStartDate);
      }
      if (!isRateLike(s.indicator.type) && targetCurrency !== 'TRY') {
        pts = pts.map((p) => {
          const converted = convertAt(p.value, 'TRY', p.date);
          return { ...p, value: converted ?? p.value };
        });
      }
      pts = forwardFillToToday(pts);
      return { ...s, points: pts };
    });
  }, [rawSeriesData, commonStartDate, targetCurrency, convertAt, bounds.from]);

  const seriesData = convertedData;

  const normalize = selected.length > 1;

  const option = useMemo(
    () => buildOption(seriesData, normalize, isDark, targetCurrency),
    [seriesData, normalize, isDark, targetCurrency]
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
      setSelected(selected.filter((s) => !isMacro(s.type)));
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
        {cameFrom && (
          <motion.button
            type="button"
            onClick={() => navigate(-1)}
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
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg tracking-tight leading-none">
          {t('analytics.compareTitle', { defaultValue: 'Karşılaştırma' })}
        </h1>
        <p className="mt-2 text-sm text-fg-muted max-w-2xl">
          {t('analytics.compareSubtitle', {
            defaultValue: 'Farklı varlıkları aynı grafikte yan yana getir. Tutar/tarih gerekmez — sadece geçmiş fiyat hareketleri.',
          })}
        </p>
      </header>

      <nav className="flex items-center gap-1">
        {MODES.map(({ id, labelKey, Icon }) => {
          const active = mode === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => switchMode(id)}
              className={`relative flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-lg cursor-pointer border-none transition-colors ${
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
                <span className="text-fg-muted uppercase tracking-[0.12em] text-[10px]">{ind.code}</span>
                <span className="text-fg-subtle">·</span>
                <span className="text-fg-muted uppercase tracking-[0.12em] text-[10px]">{ind.type}</span>
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
          <SearchSuggestions
            onSelect={addAsset}
            navigateOnSelect={false}
            excludeCodes={selected.map((s) => s.code)}
            filterType={modeDef.filterType}
            placeholder={mode === 'assets'
              ? t('analytics.compareSearchAssets', { defaultValue: 'Hisse, kripto, fon, döviz, emtia, bono ara…' })
              : t('analytics.compareSearchMixed', { defaultValue: 'Asset veya makro indikatör ara…' })}
          />
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
              {r.id}
            </button>
          ))}
        </div>

        {normalize && (
          <div className="flex items-center gap-2 text-[10px] font-mono text-amber-500 italic">
            <Info className="h-3 w-3" />
            {t('analytics.normalizedHintCompare', {
              defaultValue: 'Çoklu seri — başlangıçtan % değişim olarak normalize edildi',
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
            <ReactECharts option={option} style={{ height: '100%', width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
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

        {seriesData.length > 0 && <CompareInfoBar selected={seriesData} targetCurrency={targetCurrency} money={money} t={t} />}
      </Card>
    </motion.div>
  );
}
