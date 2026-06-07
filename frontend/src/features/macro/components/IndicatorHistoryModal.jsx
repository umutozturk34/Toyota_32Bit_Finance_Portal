import { useMemo } from 'react';
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
  fetchSeries,
  forwardFillToToday,
  rangeBounds,
} from '../../analytics/lib/compareSeriesUtils';
import { RANGES } from '../constants';
import { computeStats, formatDate, formatValue } from '../utils';
import { buildOption, colorFor, normalizeSelected } from './indicatorHistoryUtils';
import InfoBar from './IndicatorHistoryInfoBar';
import StatBlock from './IndicatorHistoryStatBlock';


export default function IndicatorHistoryModal({ indicator, onClose }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const navigate = useNavigate();
  const selected = useMemo(
    () => (indicator ? [normalizeSelected(indicator)] : []),
    [indicator]
  );
  const [rangeId, setRangeId] = useChartRange();

  const range = useMemo(() => RANGES.find((r) => r.id === rangeId) || RANGES[2], [rangeId]);
  const bounds = useMemo(() => rangeBounds(range.days), [range]);

  const queries = useQueries({
    queries: selected.map((s) => ({
      queryKey: ['compare-history', s.type, s.code, bounds.from, bounds.to],
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
      color: colorFor(ind),
    })),
    [selected, queries]
  );

  const seriesData = useMemo(() => {
    return rawSeriesData.map((s) => {
      let pts = s.points || [];
      // Anchor the line at the window start so it doesn't appear to float in mid-chart.
      pts = backFillToWindowStart(pts, bounds.from);
      // Skip per-day forward-fill (would smear April's reading across every day in May,
      // making tooltips show April's value as if recorded daily). But DO add one synthetic
      // point at today carrying the last real value — between publication dates the
      // reading is officially still in force, so the line should visibly extend to "now"
      // rather than ending mid-chart at the last publish date.
      pts = forwardFillToToday(pts);
      return { ...s, points: pts };
    });
  }, [rawSeriesData, bounds.from]);

  const localeTag = t('common.localeTag');
  const option = useMemo(
    () => buildOption(seriesData, false, isDark, localeTag),
    [seriesData, isDark, localeTag]
  );

  const stats = useMemo(() => computeStats(seriesData[0]?.points || []), [seriesData]);
  const primary = selected[0];
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

        {stats && primary?.unit && (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            <StatBlock label={t('marketOverview.macro.statLast', { defaultValue: 'Last' })}
              value={formatValue(primary?.lastValue, primary?.unit, t('common.localeTag'))}
              sub={formatDate(primary?.lastDate, t('common.localeTag'))}
              accent={primaryAccent} highlight />
            <StatBlock label={t('marketOverview.macro.statMin', { defaultValue: 'Min' })}
              value={formatValue(stats.min, primary?.unit, t('common.localeTag'))} />
            <StatBlock label={t('marketOverview.macro.statMax', { defaultValue: 'Max' })}
              value={formatValue(stats.max, primary?.unit, t('common.localeTag'))} />
            <StatBlock label={t('marketOverview.macro.statAvg', { defaultValue: 'Avg' })}
              value={formatValue(stats.avg, primary?.unit, t('common.localeTag'))}
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
