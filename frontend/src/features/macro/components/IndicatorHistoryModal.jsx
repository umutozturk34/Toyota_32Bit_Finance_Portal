import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueries } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import { GitCompareArrows } from 'lucide-react';
import { useTheme } from '../../../shared/context/useTheme';
import useChartRange from '../../../shared/hooks/useChartRange';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Spinner from '../../../shared/components/feedback/Spinner';
import {
  backFillToWindowStart,
  deriveRateSeries,
  fetchSeries,
  forwardFillToToday,
  rangeBounds,
} from '../../analytics/lib/compareSeriesUtils';
import { RANGES } from '../constants';
import { computeStats, formatDate, formatValue } from '../utils';
import { buildOption, colorFor, normalizeSelected } from './indicatorHistoryUtils';
import InfoBar from './IndicatorHistoryInfoBar';
import StatBlock from './IndicatorHistoryStatBlock';

// Inflation / PPI are stored as a cumulative INDEX (it only ever rises); offer a derived RATE view so the
// chart can show the year-over-year / month-over-month change at each date — the figure users actually read
// as "inflation" — instead of only the index level.
const RATE_VIEWS = [
  { id: 'index', labelKey: 'viewIndex' },
  { id: 'yoy', labelKey: 'viewYoy' },
  { id: 'mom', labelKey: 'viewMom' },
];
// A YoY point needs the index ~12 months earlier; widen the fetch by that much (plus slack) so the rate
// line covers the whole selected range. The display is still trimmed to the window via backFillToWindowStart.
const RATE_LOOKBACK_DAYS = 400;

export default function IndicatorHistoryModal({ indicator, onClose }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const selected = useMemo(
    () => (indicator ? [normalizeSelected(indicator)] : []),
    [indicator]
  );
  const primary = selected[0] || null;
  const [rangeId, setRangeId] = useChartRange();
  const [view, setView] = useState('index');

  const range = useMemo(() => RANGES.find((r) => r.id === rangeId) || RANGES[2], [rangeId]);
  const bounds = useMemo(() => rangeBounds(range.days), [range]);
  // Always fetch with the rate lookback so toggling Index↔Rate never refetches; for the index view the
  // extra left history is collapsed to a single anchor at the window start, leaving it visually unchanged.
  const fetchBounds = useMemo(() => {
    const d = new Date(bounds.from);
    d.setDate(d.getDate() - RATE_LOOKBACK_DAYS);
    return { from: d.toISOString().slice(0, 10), to: bounds.to };
  }, [bounds]);

  // Only an index-based inflation/PPI series can be turned into a rate; rate-type indicators stay as-is.
  const isInflationIndex = primary?.unit === 'INDEX'
    && (primary?.category === 'INFLATION' || primary?.type === 'MACRO_INFLATION');
  const activeView = isInflationIndex ? view : 'index';
  const displayUnit = activeView === 'index' ? primary?.unit : 'PERCENT';

  const queries = useQueries({
    queries: selected.map((s) => ({
      queryKey: ['compare-history', s.type, s.code, fetchBounds.from, fetchBounds.to],
      queryFn: () => fetchSeries(s, fetchBounds),
      enabled: !!s.code,
      staleTime: 5 * 60 * 1000,
    })),
  });

  const isLoading = queries.some((q) => q.isLoading);

  const rawSeriesData = useMemo(
    () => selected.map((ind, idx) => ({
      indicator: ind,
      points: queries[idx]?.data || [],
      color: colorFor(ind),
    })),
    [selected, queries]
  );

  const seriesData = useMemo(() => {
    return rawSeriesData.map((s) => {
      const source = activeView === 'index'
        ? (s.points || [])
        : deriveRateSeries(s.points || [], activeView);
      // Anchor the line at the window start so it doesn't float in mid-chart; extend the last reading to
      // today since it stays officially in force between publication dates.
      let pts = backFillToWindowStart(source, bounds.from);
      pts = forwardFillToToday(pts);
      // Chart legend/tooltip must read the localized indicator name ("TÜFE (Enflasyon)"), never the raw EVDS
      // code or the internal label key ("cpiIndex"); buildOption falls back to code only when this is absent.
      const seriesName = s.indicator.label
        ? t(`marketOverview.macro.${s.indicator.label}`, { defaultValue: s.indicator.name || s.indicator.code })
        : (s.indicator.name || s.indicator.code);
      return { ...s, indicator: { ...s.indicator, unit: displayUnit, name: seriesName }, points: pts };
    });
  }, [rawSeriesData, bounds.from, activeView, displayUnit, t]);

  const localeTag = t('common.localeTag');
  const option = useMemo(
    () => buildOption(seriesData, false, isDark, localeTag),
    [seriesData, isDark, localeTag]
  );

  const stats = useMemo(() => computeStats(seriesData[0]?.points || []), [seriesData]);
  // The "Last" reading: the index's official cached latest, or — in a rate view — the most recent derived point.
  const rateLast = useMemo(() => {
    if (activeView === 'index') return null;
    const pts = deriveRateSeries(rawSeriesData[0]?.points || [], activeView);
    return pts.length ? pts[pts.length - 1] : null;
  }, [rawSeriesData, activeView]);
  const lastValue = activeView === 'index' ? primary?.lastValue : rateLast?.value;
  const lastDate = activeView === 'index' ? primary?.lastDate : rateLast?.date;

  const primaryAccent = primary ? colorFor(primary) : '#6366f1';
  const label = primary
    ? t(`marketOverview.macro.${primary.label}`, { defaultValue: primary.name })
    : '';
  const titleText = label;

  function openCompare() {
    if (!primary) return;
    const params = new URLSearchParams({ codes: primary.code, types: primary.type });
    if (onClose) onClose();
    navigate(`/analytics?${params.toString()}`);
  }

  return (
    <BaseModal isOpen={!!indicator} onClose={onClose} title={titleText} size="5xl">
      <div className="space-y-4">
        <div className="flex items-center gap-1.5 flex-wrap">
          {seriesData.map(({ indicator: ind, color }) => (
            <span
              key={`${ind.type}-${ind.code}`}
              className="inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[10px] font-mono"
              style={{ background: `${color}14`, boxShadow: `inset 0 0 0 1px ${color}40` }}
            >
              <span className="h-1.5 w-1.5 rounded-full" style={{ background: color }} />
              <span className="text-fg-muted uppercase tracking-[0.12em]">
                {ind.label ? t(`marketOverview.macro.${ind.label}`, { defaultValue: ind.name || ind.code }) : (ind.name || ind.code)}
              </span>
              <span className="text-fg-subtle">·</span>
              <span className="text-fg-muted uppercase tracking-[0.12em]">
                {t(`marketOverview.macro.enum.${ind.type}`, { defaultValue: ind.type })}
              </span>
            </span>
          ))}
        </div>

        {primary?.label && (() => {
          const desc = t(`marketOverview.macro.descriptions.${primary.label}`, { defaultValue: '' });
          return desc ? <p className="text-[11px] text-fg-muted leading-relaxed px-0.5">{desc}</p> : null;
        })()}

        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex flex-wrap items-center gap-1">
            {RANGES.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => setRangeId(r.id)}
                className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
                  rangeId === r.id ? 'text-fg' : 'text-fg-muted hover:text-fg'
                }`}
                style={rangeId === r.id ? { background: `${primaryAccent}22`, boxShadow: `inset 0 0 0 1px ${primaryAccent}66` } : {}}
              >
                {t(`marketOverview.macro.${r.labelKey}`, { defaultValue: r.id })}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-2">
            {isInflationIndex && (
              <div className="inline-flex items-center rounded-md border border-border-default overflow-hidden">
                {RATE_VIEWS.map((v) => (
                  <button
                    key={v.id}
                    type="button"
                    onClick={() => setView(v.id)}
                    className={`text-[11px] font-mono font-semibold px-2.5 py-1 border-none cursor-pointer transition-colors ${
                      view === v.id ? 'text-fg' : 'text-fg-muted hover:text-fg'
                    }`}
                    style={view === v.id ? { background: `${primaryAccent}22`, boxShadow: `inset 0 0 0 1px ${primaryAccent}66` } : {}}
                  >
                    {v.id === 'index'
                      ? t('marketOverview.macro.viewIndex')
                      : `${t(`marketOverview.macro.${v.labelKey}`)} %`}
                  </button>
                ))}
              </div>
            )}
            <button
              type="button"
              onClick={openCompare}
              className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-[11px] font-mono font-semibold text-accent hover:text-fg border border-accent/40 hover:border-accent/70 bg-accent/8 hover:bg-accent/15 transition-colors cursor-pointer"
              title={t('marketOverview.macro.compareCta', { defaultValue: 'Karşılaştırma sayfasında aç' })}
            >
              <GitCompareArrows className="h-3 w-3" />
              {t('marketOverview.macro.compareCta', { defaultValue: 'Karşılaştır' })}
            </button>
          </div>
        </div>

        {stats && displayUnit && (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <StatBlock label={t('marketOverview.macro.statLast', { defaultValue: 'Last' })}
              value={formatValue(lastValue, displayUnit, localeTag)}
              sub={formatDate(lastDate, localeTag)}
              accent={primaryAccent} highlight />
            <StatBlock label={t('marketOverview.macro.statMin', { defaultValue: 'Min' })}
              value={formatValue(stats.min, displayUnit, localeTag)} />
            <StatBlock label={t('marketOverview.macro.statMax', { defaultValue: 'Max' })}
              value={formatValue(stats.max, displayUnit, localeTag)} />
            <StatBlock label={t('marketOverview.macro.statAvg', { defaultValue: 'Avg' })}
              value={formatValue(stats.avg, displayUnit, localeTag)}
              sub={`${stats.count} ${t('marketOverview.macro.points', { defaultValue: 'puan' })}`} />
          </div>
        )}

        <div
          className="relative rounded-xl border border-border-default/60 bg-bg-base/40 overflow-hidden h-[200px] sm:h-[300px] lg:h-[380px]"
          style={{ touchAction: 'pan-y' }}
        >
          {isLoading && (
            <div className="absolute inset-0 flex items-center justify-center">
              <Spinner size="md" tone="accent" />
            </div>
          )}
          {!isLoading && seriesData.some((s) => s.points.length > 0) && (
            <ReactECharts option={option} style={{ height: '100%', width: '100%' }} opts={{ renderer: 'canvas' }} notMerge />
          )}
          {!isLoading && seriesData.every((s) => s.points.length === 0) && (
            <div className="absolute inset-0 flex items-center justify-center text-xs text-fg-muted font-mono">
              {t('marketOverview.macro.noData', { defaultValue: 'No data in this range' })}
            </div>
          )}
        </div>

        <InfoBar selected={seriesData} t={t} />
      </div>
    </BaseModal>
  );
}
