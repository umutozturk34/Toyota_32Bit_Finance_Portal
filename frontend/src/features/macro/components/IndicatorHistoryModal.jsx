import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueries } from '@tanstack/react-query';
import ReactECharts from 'echarts-for-react';
import { Info, GitCompareArrows } from 'lucide-react';
import { useTheme } from '../../../shared/context/useTheme';
import useChartRange from '../../../shared/hooks/useChartRange';
import BaseModal from '../../../shared/components/modal/BaseModal';
import Spinner from '../../../shared/components/feedback/Spinner';
import { unifiedMarketService } from '../../../shared/services/unifiedMarketService';
import { bondService } from '../../bond/services/bondService';
import { macroIndicatorService } from '../services/macroIndicatorService';
import { RANGES } from '../constants';
import { computeStats, formatDate, formatValue, themeFor } from '../utils';


function toIso(d) {
  return d.toISOString().slice(0, 10);
}

function rangeBounds(days) {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - days);
  return { from: toIso(from), to: toIso(to) };
}

function isMacro(type) {
  return type && type.startsWith('MACRO');
}

const MACRO_CATEGORY_TO_TYPE = {
  DEPOSIT: 'MACRO_DEPOSIT',
  INFLATION: 'MACRO_INFLATION',
  RATES: 'MACRO_RATE',
};

function normalizeSelected(raw, fallbackType) {
  let type = raw.type || raw.assetType || fallbackType;
  if (!type && raw.category) type = MACRO_CATEGORY_TO_TYPE[raw.category];
  if (!type) type = 'STOCK';
  return {
    type,
    code: raw.code,
    name: raw.name || raw.label || raw.code,
    label: raw.label,
    unit: raw.unit,
    frequency: raw.frequency,
    category: raw.category,
    currency: raw.currency,
    maturity: raw.maturity,
    lastValue: raw.lastValue,
    lastDate: raw.lastDate,
  };
}

async function fetchSeries(item, bounds) {
  if (isMacro(item.type) || item.unit) {
    const points = await macroIndicatorService.history(item.code, bounds);
    return points.map((p) => ({ date: p.observedAt, value: Number(p.value) }));
  }
  if (item.type === 'BOND') {
    const rows = await bondService.getRateHistory(item.code);
    return (rows || [])
      .map((r) => ({
        date: (r.date || r.rateDate || '').slice(0, 10),
        value: Number(r.rate ?? r.couponRate ?? r.value),
      }))
      .filter((p) => p.date && Number.isFinite(p.value)
        && p.date >= bounds.from && p.date <= bounds.to);
  }
  const candles = await unifiedMarketService.getHistory(item.type, item.code, 'ALL');
  return (candles || [])
    .map((c) => {
      const rawDate = c.candleDate || c.date || c.observedAt;
      const date = typeof rawDate === 'string' ? rawDate.slice(0, 10) : null;
      const value = Number(c.close ?? c.price ?? c.sellingPrice ?? c.value ?? c.rate);
      return { date, value };
    })
    .filter((p) => p.date && Number.isFinite(p.value)
      && p.date >= bounds.from && p.date <= bounds.to);
}

function colorFor(item) {
  if (isMacro(item.type) && item.category) {
    return themeFor(item.category).accent;
  }
  return '#5E6AD2';
}

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

  const seriesData = rawSeriesData;

  const option = useMemo(
    () => buildOption(seriesData, false, isDark),
    [seriesData, isDark]
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
    <BaseModal isOpen onClose={onClose} title={titleText} size="4xl">
      <div className="space-y-4">
        <div className="flex items-center gap-1.5 flex-wrap">
          {seriesData.map(({ indicator: ind, color }) => (
            <span
              key={`${ind.type}-${ind.code}`}
              className="inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[10px] font-mono"
              style={{ background: `${color}14`, boxShadow: `inset 0 0 0 1px ${color}40` }}
            >
              <span className="h-1.5 w-1.5 rounded-full" style={{ background: color }} />
              <span className="text-fg-muted uppercase tracking-[0.12em]">{ind.code}</span>
              <span className="text-fg-subtle">·</span>
              <span className="text-fg-muted uppercase tracking-[0.12em]">{ind.type}</span>
            </span>
          ))}
        </div>

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
                {r.id}
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
              value={formatValue(primary?.lastValue, primary?.unit)}
              sub={formatDate(primary?.lastDate)}
              accent={primaryAccent} highlight />
            <StatBlock label={t('marketOverview.macro.statMin', { defaultValue: 'Min' })}
              value={formatValue(stats.min, primary?.unit)} />
            <StatBlock label={t('marketOverview.macro.statMax', { defaultValue: 'Max' })}
              value={formatValue(stats.max, primary?.unit)} />
            <StatBlock label={t('marketOverview.macro.statAvg', { defaultValue: 'Avg' })}
              value={formatValue(stats.avg, primary?.unit)}
              sub={`${stats.count} pts`} />
          </div>
        )}

        <div className="relative rounded-xl border border-border-default/60 bg-bg-base/40 overflow-hidden h-[220px] sm:h-[360px] lg:h-[460px]">
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

function InfoBar({ selected, t }) {
  if (!selected || selected.length === 0) return null;
  return (
    <div className="rounded-lg border border-border-default/40 bg-bg-base/30 p-3 space-y-1.5">
      <div className="flex items-center gap-1.5 text-[9px] font-mono uppercase tracking-[0.16em] text-fg-muted">
        <Info className="h-3 w-3" />
        {t('marketOverview.macro.indicatorInfo', { defaultValue: 'Bilgi' })}
      </div>
      {selected.map(({ indicator: ind, color }) => {
        const tags = [ind.type];
        if (ind.category) tags.push(ind.category);
        if (ind.frequency) tags.push(ind.frequency);
        if (ind.unit) tags.push(ind.unit);
        if (ind.currency) tags.push(ind.currency);
        if (ind.maturity) tags.push(ind.maturity);
        return (
          <div key={`${ind.type}-${ind.code}`} className="flex items-baseline gap-2 text-[11px]">
            <span className="h-1.5 w-1.5 rounded-full shrink-0 mt-1" style={{ background: color }} />
            <span className="font-mono text-[10px] text-fg-muted uppercase tracking-[0.12em] shrink-0">{ind.code}</span>
            <span className="text-fg-subtle">·</span>
            <span className="text-fg-muted truncate">{ind.name}</span>
            <span className="ml-auto flex items-center gap-1.5 text-[10px] font-mono text-fg-subtle uppercase tracking-[0.12em]">
              {tags.filter(Boolean).map((tag, i) => (
                <span key={`${tag}-${i}`}>
                  {i > 0 && <span className="mr-1.5">·</span>}{tag}
                </span>
              ))}
            </span>
          </div>
        );
      })}
    </div>
  );
}

function StatBlock({ label, value, sub, accent, highlight }) {
  return (
    <div
      className={`rounded-lg px-3 py-2 border ${highlight ? '' : 'border-border-default/60 bg-bg-base/40'}`}
      style={highlight ? { background: `${accent}10`, borderColor: `${accent}40` } : {}}
    >
      <p className="text-[9px] font-mono uppercase tracking-[0.14em] text-fg-muted">{label}</p>
      <p className="mt-0.5 font-mono tabular-nums font-bold text-fg text-base">{value}</p>
      {sub && <p className="text-[10px] text-fg-subtle font-mono mt-0.5">{sub}</p>}
    </div>
  );
}

function buildOption(seriesData, normalize, isDark) {
  const muted = isDark ? '#6b6b7a' : '#94a3b8';
  const grid = isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)';
  const tooltipBg = isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)';
  const tooltipFg = isDark ? '#e2e2ea' : '#1a1a2e';
  const single = seriesData.length === 1;

  const series = seriesData.map(({ indicator: ind, points, color }) => {
    if (!points || points.length === 0) return null;
    const sortedPoints = [...points].sort((a, b) =>
      String(a.date).localeCompare(String(b.date)));
    const basePoint = Number(sortedPoints[0]?.value);
    const data = sortedPoints.map((p) => {
      const raw = Number(p.value);
      const plotted = normalize && basePoint !== 0
        ? ((raw - basePoint) / Math.abs(basePoint)) * 100
        : raw;
      const pct = basePoint !== 0 ? ((raw - basePoint) / Math.abs(basePoint)) * 100 : 0;
      return [new Date(p.date).getTime(), plotted, raw, pct];
    });
    return {
      name: ind.code,
      type: 'line',
      smooth: data.length < 200,
      showSymbol: false,
      sampling: 'lttb',
      data,
      itemStyle: { color },
      lineStyle: { width: 2, color },
      areaStyle: single ? {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: `${color}55` },
            { offset: 1, color: `${color}00` },
          ],
        },
      } : null,
      _unit: ind.unit,
    };
  }).filter(Boolean);

  const totalPoints = series.reduce((acc, s) => acc + (s.data?.length || 0), 0);
  const showZoom = totalPoints >= 2;
  series.forEach((s, idx) => {
    s.animationDuration = 1100;
    s.animationEasing = 'cubicOut';
    s.animationDelay = idx * 180;
  });

  return {
    backgroundColor: 'transparent',
    animation: true,
    animationThreshold: 100000,
    grid: { left: 56, right: 16, top: single ? 16 : 32, bottom: showZoom ? 64 : 32, containLabel: false },
    legend: !single ? {
      type: 'scroll',
      top: 4,
      textStyle: { color: muted, fontSize: 10, fontFamily: 'ui-monospace,monospace' },
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
    } : undefined,
    dataZoom: showZoom ? [
      {
        type: 'inside',
        filterMode: 'filter',
        zoomOnMouseWheel: true,
        moveOnMouseMove: true,
        moveOnMouseWheel: false,
        preventDefaultMouseMove: true,
      },
      {
        type: 'slider',
        height: 18,
        bottom: 8,
        filterMode: 'filter',
        borderColor: 'transparent',
        backgroundColor: 'transparent',
        dataBackground: {
          lineStyle: { color: '#6366f160', width: 1 },
          areaStyle: { color: '#6366f120' },
        },
        selectedDataBackground: {
          lineStyle: { color: '#6366f1', width: 1 },
          areaStyle: { color: '#6366f140' },
        },
        fillerColor: 'rgba(99,102,241,0.12)',
        handleStyle: { color: '#6366f1', borderColor: '#6366f1' },
        moveHandleStyle: { color: '#6366f1', opacity: 0.4 },
        showDetail: false,
        brushSelect: false,
        textStyle: { color: muted, fontSize: 9 },
      },
    ] : undefined,
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderWidth: 0,
      textStyle: { color: tooltipFg, fontSize: 11 },
      formatter: (params) => {
        if (!params?.length) return '';
        const date = new Date(params[0].value[0]).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' });
        const rows = params.map((p) => {
          const seriesDef = series.find((s) => s.name === p.seriesName);
          const unit = seriesDef?._unit;
          const raw = Number(p.value[2]);
          const pct = Number(p.value[3]);
          const rawFmt = unit === 'PERCENT'
            ? `%${raw.toFixed(2)}`
            : raw.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
          const sign = pct > 0 ? '+' : '';
          const pctColor = pct > 0 ? '#10b981' : pct < 0 ? '#ef4444' : tooltipFg;
          const pctFmt = `${sign}${pct.toFixed(2)}%`;
          return `<div style="display:flex;justify-content:space-between;gap:14px;align-items:center;padding:3px 0;font-family:ui-monospace,monospace;font-size:11px">
            <span style="display:flex;align-items:center;gap:6px;min-width:0">
              <span style="width:6px;height:6px;border-radius:50%;background:${p.color};flex-shrink:0"></span>
              <span style="color:${tooltipFg};opacity:0.85">${p.seriesName}</span>
            </span>
            <span style="display:flex;align-items:baseline;gap:8px;flex-shrink:0">
              <span style="font-weight:700;color:${p.color}">${rawFmt}</span>
              <span style="font-size:10px;font-weight:600;color:${pctColor};opacity:0.9">${pctFmt}</span>
            </span>
          </div>`;
        }).join('');
        return `<div style="padding:6px 4px;min-width:220px">
          <div style="font-size:10px;color:${tooltipFg};opacity:0.65;margin-bottom:6px">${date}</div>
          ${rows}
        </div>`;
      },
    },
    xAxis: {
      type: 'time',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: muted, fontSize: 10 },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: muted, fontSize: 10,
        formatter: (val) => {
          if (normalize) {
            const sign = val > 0 ? '+' : '';
            return `${sign}${val.toFixed(0)}%`;
          }
          const unit = series[0]?._unit;
          return unit === 'PERCENT' ? `%${val.toFixed(1)}` : val.toLocaleString('tr-TR');
        },
      },
      splitLine: { lineStyle: { color: grid, type: 'dashed' } },
    },
    series,
  };
}
