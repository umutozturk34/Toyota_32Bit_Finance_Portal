import { useCallback, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { Scale, TrendingUp, TrendingDown } from 'lucide-react';
import { formatPrice } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import FitMoney from '../../../shared/components/FitMoney';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import { RANGE_OPTIONS } from '../../../shared/components/form/rangeSelectorOptions';
import { useTheme } from '../../../shared/context/useTheme';
import i18n from '../../../shared/i18n/config';
import { timeAxis, valueAxis, dataZoomBlock, lineSeriesDefaults, areaGradient } from '../../../shared/charts/echartsTheme';
import { useFixedIncomeHistory } from '../hooks/useFixedIncomePositions';
import { useMacroIndicatorHistory, useMacroIndicators } from '../../macro/hooks/useMacroIndicators';

const FIXED_PERIODS = ['1M', '6M', '1Y', '3Y', '5Y', 'ALL'];
const PERIOD_OPTIONS = RANGE_OPTIONS.filter((o) => FIXED_PERIODS.includes(o.id));
const PNL_COLOR = '#6366f1';
const INFLATION_COLOR = '#f59e0b';
const DAY_MS = 24 * 3600 * 1000;

function palette(isDark) {
  return isDark
    ? { fg: '#e2e2ea', muted: '#6b6b7a', grid: 'rgba(255,255,255,0.05)' }
    : { fg: '#1a1a2e', muted: '#94a3b8', grid: 'rgba(0,0,0,0.04)' };
}

// Forward-filled CPI index at time t from the (sorted) macro series: the latest observation on or before t,
// falling back to the earliest known value when t predates the series. CPI is monthly while the value series is
// daily, so the carry-forward keeps every day on the last published index.
function cpiAtFactory(cpiSorted) {
  return (t) => {
    if (cpiSorted.length === 0) return null;
    let chosen = cpiSorted[0].v;
    for (const c of cpiSorted) {
      if (c.t <= t) chosen = c.v;
      else break;
    }
    return chosen;
  };
}

/**
 * Builds the P&L-vs-inflation series. The main line is realized+unrealized K/Z (value − cost). The breakeven line
 * grows each cost increment (the day-over-day rise in cumulative cost as holdings come online) by the CPI ratio
 * since that day, then subtracts cost — i.e. the K/Z you'd need JUST to keep pace with inflation over the window.
 * When the K/Z line sits above the breakeven line, the book is beating inflation.
 */
function buildSeries(history, allHistory, cpi) {
  const rows = Array.isArray(history) ? history : [];
  if (rows.length === 0) return [];
  const cpiSorted = (Array.isArray(cpi) ? cpi : [])
    .map((p) => ({ t: new Date(p?.observedAt ?? p?.date).getTime(), v: p?.value != null ? Number(p.value) : null }))
    .filter((p) => p.v != null && Number.isFinite(p.t))
    .sort((a, b) => a.t - b.t);
  const cpiAt = cpiAtFactory(cpiSorted);

  // Cost increments are derived from the FULL history (each holding's TRUE entry date), not the displayed window —
  // so capital committed before the window is grown by inflation from when it was actually deployed, not from the
  // window's left edge (which understated the inflation hurdle on bounded ranges). Each increment carries its date.
  const costRows = (Array.isArray(allHistory) && allHistory.length > 0) ? allHistory : rows;
  const increments = [];
  let prevCost = 0;
  for (const r of costRows) {
    const t = new Date(r.date).getTime();
    const cost = Number(r.totalCostTry) || 0;
    const delta = cost - prevCost;
    prevCost = cost;
    const cpi0 = cpiAt(t);
    if (delta !== 0 && cpi0 != null) increments.push({ t, cpi0, amount: delta });
  }

  return rows.map((r) => {
    const t = new Date(r.date).getTime();
    // The backend supplies the cumulative coupon cash received by this date (each coupon priced at its own
    // historical rate); add it back to value so the K/Z line doesn't dip on an ex-coupon price drop.
    const value = (Number(r.totalValueTry) || 0) + (Number(r.bondCouponsReceivedTry) || 0);
    const cost = Number(r.totalCostTry) || 0;
    const cpiNow = cpiAt(t);
    let inflationValue = cost;
    if (cpiNow != null) {
      inflationValue = increments.reduce(
        (sum, inc) => (inc.t <= t ? sum + (inc.cpi0 ? inc.amount * (cpiNow / inc.cpi0) : inc.amount) : sum), 0);
    }
    return { time: t, pnl: value - cost, inflationPnl: inflationValue - cost };
  });
}

function FixedIncomePnlChart({ portfolioId }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const [period, setPeriod] = useState('1Y');
  const { data: history = [], isLoading } = useFixedIncomeHistory(portfolioId, period);
  // The full history (regardless of the displayed window) anchors each cost increment to its true entry date so the
  // inflation hurdle compounds from when capital was actually committed. When period==='ALL' this resolves to the
  // same cached query as `history`, so bounded windows are the only ones paying the extra fetch.
  const { data: allHistory = [] } = useFixedIncomeHistory(portfolioId, 'ALL');

  const to = history.length > 0 ? String(history[history.length - 1].date).slice(0, 10) : undefined;
  // CPI is exposed under the LABEL "cpiIndex" but the history endpoint resolves by CODE (the EVDS identifier,
  // e.g. TP.GENENDEKS.T1) — resolve the real code from the inflation indicator list rather than passing the label
  // (passing the label hit findByCode("cpiIndex") → empty → a flat, inflation-less breakeven line).
  const { data: inflationIndicators = [] } = useMacroIndicators({ category: 'INFLATION' });
  const cpiCode = useMemo(() => {
    const list = Array.isArray(inflationIndicators) ? inflationIndicators : [];
    const cpi = list.find((i) => i.label === 'cpiIndex')
      || list.find((i) => i.code?.includes('GENENDEKS'))
      || list[0];
    return cpi?.code;
  }, [inflationIndicators]);
  // Fetch CPI from ~2 months before the window so the monthly index always has a baseline observation on or
  // before the first plotted day for the forward-fill.
  // Anchor the CPI fetch to the EARLIEST entry (full history), not the window start, so every cost increment has a
  // baseline index observation on or before it for the forward-fill — otherwise pre-window increments would grow
  // from the wrong (later) CPI baseline.
  const cpiFrom = useMemo(() => {
    const first = allHistory.length > 0 ? allHistory[0] : history[0];
    if (!first) return undefined;
    const d = new Date(`${String(first.date).slice(0, 10)}T00:00:00`);
    d.setDate(d.getDate() - 70);
    return d.toISOString().slice(0, 10);
  }, [allHistory, history]);
  const { data: cpi = [] } = useMacroIndicatorHistory(cpiCode, { from: cpiFrom, to });

  const points = useMemo(() => buildSeries(history, allHistory, cpi), [history, allHistory, cpi]);
  const last = points.length > 0 ? points[points.length - 1] : null;
  const beating = last != null && last.pnl >= last.inflationPnl;

  const money = useCallback((value) => {
    if (value == null) return '—';
    const abs = Math.abs(value);
    const maxDecimals = abs < 10 ? 4 : abs < 1000 ? 2 : 0;
    return formatPrice(value, { currency: 'TRY', minDecimals: 0, maxDecimals });
  }, []);

  const option = useMemo(() => {
    if (points.length === 0) return null;
    const pal = palette(isDark);
    const showZoom = points.length >= 2;
    return {
      backgroundColor: 'transparent',
      animation: points.length < 200,
      grid: { left: 8, right: 12, top: 16, bottom: showZoom ? 48 : 32, containLabel: true },
      dataZoom: showZoom ? dataZoomBlock(pal) : [],
      tooltip: {
        trigger: 'axis',
        confine: true,
        backgroundColor: isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)',
        borderWidth: 0,
        textStyle: { color: pal.fg, fontSize: 11 },
        formatter: (params) => {
          const p = params?.[0];
          if (!p) return '';
          const ts = p.value[0];
          const date = new Date(ts).toLocaleDateString(i18n.t('common.localeTag'), {
            day: '2-digit', month: 'short', year: 'numeric',
          });
          const rows = (params || []).map((s) => {
            const sign = s.value[1] >= 0 ? '+' : '−';
            return `<div style="display:flex;align-items:center;gap:6px;font-size:11px">`
              + `<span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${s.color}"></span>`
              + `<span style="color:${pal.muted}">${s.seriesName}</span>`
              + `<span style="margin-left:auto;font-family:ui-monospace,monospace;color:${pal.fg}">${sign} ${money(Math.abs(s.value[1]))}</span></div>`;
          }).join('');
          return `<div style="font-size:10px;color:${pal.muted};margin-bottom:4px">${date}</div>${rows}`;
        },
      },
      xAxis: timeAxis(pal, {
        axisLabel: {
          color: pal.muted, fontSize: 10, hideOverlap: true,
          formatter: (val) => {
            const d = new Date(val);
            return `${d.getDate()} ${d.toLocaleString(i18n.t('common.localeTag'), { month: 'short' })}`;
          },
        },
        minInterval: DAY_MS,
      }),
      yAxis: valueAxis(pal, {
        axisLabel: { color: pal.muted, fontSize: 10, formatter: (val) => money(val) },
      }),
      series: [
        {
          ...lineSeriesDefaults(PNL_COLOR, points.length),
          name: t('portfolio.fixedIncome.pnl.pnlLabel'),
          data: points.map((d) => [d.time, d.pnl]),
          areaStyle: { color: areaGradient(PNL_COLOR) },
          emphasis: { focus: 'series' },
          markLine: {
            symbol: ['none', 'none'], silent: true,
            lineStyle: { color: pal.muted, width: 1, type: 'dashed', opacity: 0.5 },
            label: { show: false },
            data: [{ yAxis: 0 }],
          },
        },
        {
          ...lineSeriesDefaults(INFLATION_COLOR, points.length),
          name: t('portfolio.fixedIncome.pnl.inflationLabel'),
          data: points.map((d) => [d.time, d.inflationPnl]),
          lineStyle: { color: INFLATION_COLOR, width: 1.5, type: 'dashed' },
          symbol: 'none',
          emphasis: { focus: 'series' },
        },
      ],
    };
  }, [points, isDark, money, t]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group">
        <div
          className="pointer-events-none absolute -top-20 -left-20 w-44 h-44 rounded-full blur-[80px] opacity-0 group-hover:opacity-60 transition-opacity duration-500"
          style={{ background: `radial-gradient(circle, ${PNL_COLOR}20 0%, transparent 70%)` }}
        />

        <div className="flex items-center justify-between p-4 sm:p-5 pb-0 gap-3 flex-wrap">
          <div className="flex items-center gap-3 min-w-0">
            <span
              className="flex items-center justify-center w-10 h-10 rounded-xl transition-transform duration-300 group-hover:scale-105"
              style={{ backgroundColor: `${PNL_COLOR}15`, boxShadow: `0 0 20px ${PNL_COLOR}10` }}
            >
              <Scale className="h-4 w-4 text-accent" />
            </span>
            <div className="min-w-0">
              <p className="text-sm font-bold text-fg">{t('portfolio.fixedIncome.pnl.title')}</p>
              <p className="text-[11px] text-fg-muted">{t('portfolio.fixedIncome.pnl.subtitle')}</p>
            </div>
          </div>
          <div className="flex items-center gap-2 flex-wrap justify-end">
            {last != null && (
              <span className={`inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold ${
                beating ? 'bg-success/12 text-success' : 'bg-danger/12 text-danger'}`}>
                {beating ? <TrendingUp className="h-3.5 w-3.5" /> : <TrendingDown className="h-3.5 w-3.5" />}
                {t(beating ? 'portfolio.fixedIncome.pnl.beat' : 'portfolio.fixedIncome.pnl.behind')}
              </span>
            )}
            <div className="max-w-full overflow-x-auto">
              <RangeSelector value={period} onChange={setPeriod} layoutId="fixed-income-pnl-range" size="md" options={PERIOD_OPTIONS} />
            </div>
          </div>
        </div>

        <div className="px-4 sm:px-5 pt-3 flex items-center gap-3 text-[11px] text-fg-muted">
          <span className="inline-flex items-center gap-1.5">
            <span className="h-2 w-3 rounded-sm" style={{ background: PNL_COLOR }} />
            {t('portfolio.fixedIncome.pnl.pnlLabel')}
            {last != null && (
              <FitMoney value={last.pnl} base="TRY" pinned className={`font-mono font-semibold ${last.pnl >= 0 ? 'text-success' : 'text-danger'}`} />
            )}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block w-3 border-t-2 border-dashed" style={{ borderColor: INFLATION_COLOR }} />
            {t('portfolio.fixedIncome.pnl.inflationLabel')}
          </span>
        </div>

        <div className="relative min-h-[240px] sm:min-h-[320px] px-2 pt-3">
          {isLoading ? (
            <div className="absolute inset-0 flex items-center justify-center">
              <Spinner size="md" tone="accent" />
            </div>
          ) : option ? (
            <ReactECharts
              key={isDark}
              option={option}
              notMerge
              lazyUpdate
              style={{ height: 'min(48vh, 320px)', minHeight: 240, width: '100%' }}
              opts={{ renderer: 'canvas' }}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-[240px] sm:h-[320px] gap-3">
              <Scale className="h-8 w-8 text-fg-subtle" />
              <p className="text-sm text-fg-muted">{t('portfolio.fixedIncome.pnl.empty')}</p>
            </div>
          )}
        </div>
      </Card>
    </motion.div>
  );
}

export default FixedIncomePnlChart;
