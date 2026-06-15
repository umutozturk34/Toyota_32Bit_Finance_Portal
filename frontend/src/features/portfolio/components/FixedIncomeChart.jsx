import { useCallback, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { TrendingUp } from '../../../shared/components/feedback/AnimatedIcons';
import { formatPrice } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import Card from '../../../shared/components/card';
import Spinner from '../../../shared/components/feedback/Spinner';
import FitMoney from '../../../shared/components/FitMoney';
import RangeSelector from '../../../shared/components/form/RangeSelector';
import { RANGE_OPTIONS } from '../../../shared/components/form/rangeSelectorOptions';
import { useTheme } from '../../../shared/context/useTheme';
import i18n from '../../../shared/i18n/config';
import {
  timeAxis,
  valueAxis,
  dataZoomBlock,
  lineSeriesDefaults,
  areaGradient,
} from '../../../shared/charts/echartsTheme';
import { useFixedIncomeHistory, useBonds } from '../hooks/useFixedIncomePositions';

const FIXED_PERIODS = ['1M', '6M', '1Y', '3Y', '5Y', 'ALL'];
const PERIOD_OPTIONS = RANGE_OPTIONS.filter((o) => FIXED_PERIODS.includes(o.id));
const MAIN_COLOR = '#6366f1';
const COUPON_COLOR = '#10b981';

const MODE_VALUE_KEY = { total: 'totalValueTry', deposit: 'depositValueTry', bond: 'bondValueTry' };
const PAYMENTS_PER_YEAR = { ANNUAL: 1, SEMI_ANNUAL: 2, QUARTERLY: 4, MONTHLY: 12, ZERO_COUPON: 0 };
const DAY_MS = 24 * 3600 * 1000;

/** Months between coupon dates for a holding's frequency (0 = zero-coupon). Defaults to semi-annual. */
function stepMonthsFor(freq) {
  const perYear = PAYMENTS_PER_YEAR[freq] ?? 2;
  return perYear === 0 ? 0 : 12 / perYear;
}

function palette(isDark) {
  return isDark
    ? { fg: '#e2e2ea', muted: '#6b6b7a', grid: 'rgba(255,255,255,0.05)' }
    : { fg: '#1a1a2e', muted: '#94a3b8', grid: 'rgba(0,0,0,0.04)' };
}

// Treasury bonds pay semi-annual coupons: step +6 months from the issue date (maturityStart)
// to maturity, yielding every scheduled coupon date in between. Kept independent of the holding's
// entry/exit so the chart shows the bond's full coupon calendar, then clamped to the visible window.
function couponDatesFor(maturityStart, maturityEnd, stepMonths) {
  if (!maturityStart || !maturityEnd || !stepMonths) return [];
  const end = new Date(maturityEnd).getTime();
  const dates = [];
  const cursor = new Date(maturityStart);
  cursor.setMonth(cursor.getMonth() + stepMonths);
  while (cursor.getTime() <= end) {
    dates.push(cursor.getTime());
    cursor.setMonth(cursor.getMonth() + stepMonths);
  }
  return dates;
}

function FixedIncomeChart({ portfolioId }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const [period, setPeriod] = useState('1Y');
  const [mode, setMode] = useState('total');
  const { data = [], isLoading } = useFixedIncomeHistory(portfolioId, period);
  const { data: bonds = [] } = useBonds(portfolioId);

  const valueKey = MODE_VALUE_KEY[mode];

  const modeOptions = useMemo(
    () => [
      { id: 'total', label: t('portfolio.fixedIncome.modes.total') },
      { id: 'deposit', label: t('portfolio.fixedIncome.modes.deposit') },
      { id: 'bond', label: t('portfolio.fixedIncome.modes.bond') },
    ],
    [t],
  );

  const money = useCallback((value) => {
    if (value == null) return '—';
    const abs = Math.abs(value);
    const maxDecimals = abs < 10 ? 4 : abs < 1000 ? 3 : 2;
    return formatPrice(value, { currency: 'TRY', minDecimals: 2, maxDecimals });
  }, []);

  const points = useMemo(
    () => (data || []).map((d) => ({ time: new Date(d.date).getTime(), value: Number(d[valueKey]) })),
    [data, valueKey],
  );

  const latest = points.length > 0 ? points[points.length - 1].value : null;

  // Coupon markers only make sense on a series that carries the bond value (bond + total modes).
  const showCoupons = mode === 'bond' || mode === 'total';

  const couponMarks = useMemo(() => {
    if (!showCoupons || points.length === 0) return [];
    const windowStart = points[0].time;
    const windowEnd = points[points.length - 1].time;
    const seen = new Set();
    const marks = [];
    (bonds || []).forEach((bond) => {
      couponDatesFor(bond.maturityStart, bond.maturityEnd, stepMonthsFor(bond.couponFrequency)).forEach((time) => {
        if (time < windowStart || time > windowEnd || seen.has(time)) return;
        seen.add(time);
        marks.push({ time, code: bond.bondSeriesCode });
      });
    });
    return marks.sort((a, b) => a.time - b.time);
  }, [bonds, points, showCoupons]);

  const couponLabelMap = useMemo(() => {
    const map = new Map();
    couponMarks.forEach((m) => {
      const codes = map.get(m.time) || [];
      if (m.code && !codes.includes(m.code)) codes.push(m.code);
      map.set(m.time, codes);
    });
    return map;
  }, [couponMarks]);

  const option = useMemo(() => {
    if (points.length === 0) return null;
    const pal = palette(isDark);
    const showZoom = points.length >= 2;
    const markLine = couponMarks.length > 0 ? {
      symbol: ['none', 'none'],
      silent: false,
      lineStyle: { color: COUPON_COLOR, width: 1, type: 'dashed', opacity: 0.65 },
      label: { show: false },
      emphasis: { lineStyle: { width: 2, opacity: 1 } },
      data: couponMarks.map((m) => ({ xAxis: m.time })),
    } : undefined;
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
        axisPointer: { type: 'line', lineStyle: { color: `${MAIN_COLOR}55` } },
        formatter: (params) => {
          const p = params?.[0];
          if (!p) return '';
          const ts = p.value[0];
          const date = new Date(ts).toLocaleDateString(i18n.t('common.localeTag'), {
            day: '2-digit', month: 'short', year: 'numeric',
          });
          const coupons = couponLabelMap.get(ts);
          const couponRow = coupons && coupons.length > 0
            ? `<div style="display:flex;align-items:center;gap:4px;margin-top:5px;font-size:10px;color:${COUPON_COLOR}">`
              + `<span style="display:inline-block;width:6px;height:6px;border-radius:50%;background:${COUPON_COLOR}"></span>`
              + `${t('portfolio.fixedIncome.couponLabel')}: ${coupons.join(', ')}</div>`
            : '';
          return `<div style="font-size:10px;color:${pal.muted};margin-bottom:4px">${date}</div>`
            + `<div style="font-size:14px;font-weight:700;font-family:ui-monospace,monospace;color:${pal.fg}">${money(p.value[1])}</div>`
            + couponRow;
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
      series: [{
        ...lineSeriesDefaults(MAIN_COLOR, points.length),
        data: points.map((d) => [d.time, d.value]),
        areaStyle: { color: areaGradient(MAIN_COLOR) },
        emphasis: { focus: 'series' },
        ...(markLine ? { markLine } : {}),
      }],
    };
  }, [points, isDark, money, couponMarks, couponLabelMap, t]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group">
        <div
          className="pointer-events-none absolute -top-20 -left-20 w-44 h-44 rounded-full blur-[80px] opacity-0 group-hover:opacity-60 transition-opacity duration-500"
          style={{ background: `radial-gradient(circle, ${MAIN_COLOR}20 0%, transparent 70%)` }}
        />

        <div className="flex items-center justify-between p-4 sm:p-5 pb-0 gap-3 flex-wrap">
          <div className="flex items-center gap-3 min-w-0">
            <span
              className="flex items-center justify-center w-10 h-10 rounded-xl transition-transform duration-300 group-hover:scale-105"
              style={{ backgroundColor: `${MAIN_COLOR}15`, boxShadow: `0 0 20px ${MAIN_COLOR}10` }}
            >
              <TrendingUp className="h-4.5 w-4.5 text-accent" />
            </span>
            <div className="min-w-0">
              <p className="text-sm font-bold text-fg">{t('portfolio.fixedIncome.chartTitle')}</p>
              {latest != null && (
                <FitMoney value={latest} base="TRY" pinned className="text-xl font-mono font-bold text-fg tracking-tight mt-0.5" />
              )}
            </div>
          </div>
          <div className="flex items-center gap-2 flex-wrap justify-end">
            <div className="max-w-full overflow-x-auto">
              <RangeSelector value={mode} onChange={setMode} layoutId="fixed-income-mode" size="md" options={modeOptions} />
            </div>
            <div className="max-w-full overflow-x-auto">
              <RangeSelector value={period} onChange={setPeriod} layoutId="fixed-income-range" size="md" options={PERIOD_OPTIONS} />
            </div>
          </div>
        </div>

        {showCoupons && couponMarks.length > 0 && (
          <div className="px-4 sm:px-5 pt-2 flex items-center gap-1.5 text-[11px] text-fg-muted">
            <span className="inline-block w-4 border-t border-dashed" style={{ borderColor: COUPON_COLOR }} />
            <span>{t('portfolio.fixedIncome.couponLegend')}</span>
          </div>
        )}

        <div className="relative min-h-[240px] sm:min-h-[360px] px-2 pt-4">
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
              style={{ height: 'min(52vh, 360px)', minHeight: 240, width: '100%' }}
              opts={{ renderer: 'canvas' }}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-[240px] sm:h-[360px] gap-3">
              <TrendingUp className="h-8 w-8 text-fg-subtle" />
              <p className="text-sm text-fg-muted">{t('portfolio.fixedIncome.chartEmpty')}</p>
            </div>
          )}
        </div>
      </Card>
    </motion.div>
  );
}

export default FixedIncomeChart;
