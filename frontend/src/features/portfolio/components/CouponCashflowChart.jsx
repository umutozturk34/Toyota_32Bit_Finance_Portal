import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import ReactECharts from 'echarts-for-react';
import { CalendarRange, Coins, Banknote, Landmark, X } from 'lucide-react';
import { formatPrice } from '../../../shared/utils/formatters';
import { cardVariants } from '../../../shared/utils/animations';
import Card from '../../../shared/components/card';
import { useTheme } from '../../../shared/context/useTheme';
import i18n from '../../../shared/i18n/config';
import { useBonds, useDeposits } from '../hooks/useFixedIncomePositions';

const COUPON_COLOR = '#10b981';
const REDEMPTION_COLOR = '#6366f1';
// Per-cashflow-kind presentation for the clicked-month drill-in: a coupon (green), a bond redemption at par /
// indexed value (indigo), or a deposit reaching maturity (indigo). Colours mirror the two stacked bar series.
const KIND_META = {
  coupon: { color: COUPON_COLOR, Icon: Coins, labelKey: 'portfolio.fixedIncome.couponLabel' },
  redemption: { color: REDEMPTION_COLOR, Icon: Landmark, labelKey: 'portfolio.fixedIncome.redemption' },
  deposit: { color: REDEMPTION_COLOR, Icon: Banknote, labelKey: 'portfolio.fixedIncome.depositMaturityLabel' },
};
// Cap how far ahead the schedule reaches. The window auto-shrinks to the furthest actual cashflow, so a book with
// only near-term maturities stays tight, while a multi-year deposit/bond still has its redemption shown (the old
// hard 24-month clip hid every cashflow past two years — leaving the calendar wrongly "empty").
const HORIZON_MONTHS = 120;
const PAYMENTS_PER_YEAR = { ANNUAL: 1, SEMI_ANNUAL: 2, QUARTERLY: 4, MONTHLY: 12, ZERO_COUPON: 0 };
const CPI_TYPES = new Set(['FLOATING_CPI', 'SUKUK_CPI']);
const GOLD_TYPES = new Set(['GOLD', 'SUKUK_GOLD']);

function palette(isDark) {
  return isDark
    ? { fg: '#e2e2ea', muted: '#6b6b7a', grid: 'rgba(255,255,255,0.05)' }
    : { fg: '#1a1a2e', muted: '#94a3b8', grid: 'rgba(0,0,0,0.04)' };
}

function monthKey(d) {
  return d.getFullYear() * 12 + d.getMonth();
}

function stepMonthsFor(freq) {
  const perYear = PAYMENTS_PER_YEAR[freq] ?? 2;
  return perYear === 0 ? 0 : 12 / perYear;
}

// Projects every cash inflow (coupon payments + maturity redemptions) for the open holdings over the next
// HORIZON_MONTHS, bucketed by calendar month. Coupons step from each bond's next coupon date by its frequency
// up to maturity; redemptions land the bond's par face (or a deposit's current value) on the maturity month.
function buildBuckets(bonds, deposits, nowMs) {
  const startKey = monthKey(new Date(nowMs));
  const endKey = startKey + HORIZON_MONTHS;
  const coupons = new Map();
  const redemptions = new Map();
  // Per-month list of the individual cashflows behind the aggregate bars, so a clicked bucket can show WHICH bond
  // pays a coupon / redeems and WHICH deposit matures, each with its own date and amount.
  const itemsByMonth = new Map();
  const add = (map, key, amount) => { if (amount > 0) map.set(key, (map.get(key) || 0) + amount); };
  const pushItem = (key, item) => {
    if (!(item.amount > 0)) return;
    if (!itemsByMonth.has(key)) itemsByMonth.set(key, []);
    itemsByMonth.get(key).push(item);
  };

  bonds.forEach((b) => {
    if (b.exitDate) return;
    // CPI and gold bonds pay their coupon on the INDEXED / GOLD value, not the face nominal — so project them
    // against the current indexed/gold value (nominalValueTry). Future inflation is unknown, so this holds today's
    // base flat across the horizon (a reasonable estimate). A plain/floater coupon is on the face quantity.
    const indexed = CPI_TYPES.has(b.bondType) || GOLD_TYPES.has(b.bondType);
    // Coupon rides the indexed/gold value for CPI/gold, else the FACE nominal = quantity × 100 (each bond/adet is a
    // 100-nominal lot). per100ToTry semantics: amount = rate × base ÷ 100.
    const couponBase = indexed ? (Number(b.nominalValueTry) || 0) : (Number(b.quantity) || 0) * 100;
    const perYear = PAYMENTS_PER_YEAR[b.couponFrequency] ?? 2;
    const stepMonths = stepMonthsFor(b.couponFrequency);
    const name = b.bondSeriesCode || b.bondName || b.bondIsin || '—';
    if (perYear > 0 && b.couponRate != null && couponBase > 0 && b.nextCouponDate && b.maturityEnd) {
      const couponAmount = (Number(b.couponRate) / perYear) * couponBase / 100;
      const maturity = new Date(`${String(b.maturityEnd).slice(0, 10)}T00:00:00`);
      const cursor = new Date(`${String(b.nextCouponDate).slice(0, 10)}T00:00:00`);
      let guard = 0;
      while (cursor <= maturity && guard < 400) {
        const k = monthKey(cursor);
        if (k >= startKey && k < endKey) {
          add(coupons, k, couponAmount);
          pushItem(k, { kind: 'coupon', name, bondType: b.bondType, amount: couponAmount, ts: cursor.getTime() });
        }
        cursor.setMonth(cursor.getMonth() + stepMonths);
        guard += 1;
      }
    }
    // Redemption at maturity: the indexed/gold value for a CPI/gold bond, else par (100 × quantity bonds).
    if (b.maturityEnd) {
      const matDate = new Date(`${String(b.maturityEnd).slice(0, 10)}T00:00:00`);
      const k = monthKey(matDate);
      if (k >= startKey && k < endKey) {
        const amt = indexed ? Number(b.nominalValueTry) || 0 : (Number(b.quantity) || 0) * 100;
        add(redemptions, k, amt);
        pushItem(k, { kind: 'redemption', name, bondType: b.bondType, amount: amt, ts: matDate.getTime() });
      }
    }
  });

  deposits.forEach((d) => {
    if (d.active === false || d.closedDate) return;
    if (!d.maturityDate) return;
    const matDate = new Date(`${String(d.maturityDate).slice(0, 10)}T00:00:00`);
    const k = monthKey(matDate);
    if (k >= startKey && k < endKey) {
      const amt = Number(d.currentValueTry) || 0;
      add(redemptions, k, amt);
      pushItem(k, { kind: 'deposit', name: d.indicatorCode || d.currency || 'TRY', amount: amt, ts: matDate.getTime() });
    }
  });

  if (coupons.size === 0 && redemptions.size === 0) return null;

  // Contiguous buckets from today to the furthest cashflow, so EMPTY periods are visible as gaps (zero-height
  // bars) rather than hidden. Granularity adapts to the span so it never overflows the card: monthly when it fits
  // (≤ ~2 years), then quarters, then years for a long book.
  const cashKeys = [...coupons.keys(), ...redemptions.keys()];
  const maxKey = Math.max(...cashKeys);
  const span = maxKey - startKey;
  const bucketMonths = span <= 23 ? 1 : span <= 71 ? 3 : 12;
  const buckets = [];
  for (let k = startKey; k <= maxKey; k += bucketMonths) {
    let coupon = 0;
    let redemption = 0;
    const items = [];
    for (let j = k; j < k + bucketMonths; j += 1) {
      coupon += coupons.get(j) || 0;
      redemption += redemptions.get(j) || 0;
      if (itemsByMonth.has(j)) items.push(...itemsByMonth.get(j));
    }
    items.sort((a, b) => a.ts - b.ts);
    const d = new Date(Math.floor(k / 12), k % 12, 1);
    buckets.push({ ts: d.getTime(), coupon, redemption, items, bucketMonths });
  }
  return buckets;
}

export default function CouponCashflowChart({ portfolioId }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const { data: bonds = [] } = useBonds(portfolioId);
  const { data: deposits = [] } = useDeposits(portfolioId);
  const [nowMs] = useState(() => Date.now());
  const [selectedIdx, setSelectedIdx] = useState(null);

  const months = useMemo(() => buildBuckets(bonds, deposits, nowMs), [bonds, deposits, nowMs]);
  // Guard against a stale index after the book changes (e.g. switching portfolio shrinks the bucket list).
  const selected = (selectedIdx != null && months && selectedIdx < months.length) ? months[selectedIdx] : null;

  const money = (value) => {
    if (value == null) return '—';
    const abs = Math.abs(value);
    const maxDecimals = abs < 1000 ? 2 : 0;
    return formatPrice(value, { currency: 'TRY', minDecimals: 0, maxDecimals });
  };

  // Header label for a clicked bucket: the month + year (or just the year for the yearly granularity).
  const bucketLabel = (bucket) => {
    const d = new Date(bucket.ts);
    const localeTag = i18n.t('common.localeTag');
    if (bucket.bucketMonths >= 12) return String(d.getFullYear());
    return `${d.toLocaleString(localeTag, { month: 'long' })} ${d.getFullYear()}`;
  };
  // Exact pay date of a single cashflow inside the bucket.
  const itemDate = (ts) => new Date(ts).toLocaleDateString(i18n.t('common.localeTag'), {
    day: '2-digit', month: 'short', year: 'numeric',
  });

  const option = useMemo(() => {
    if (!months) return null;
    const pal = palette(isDark);
    const localeTag = i18n.t('common.localeTag');
    const labels = months.map((m) => {
      const d = new Date(m.ts);
      return `${d.toLocaleString(localeTag, { month: 'short' })} ${String(d.getFullYear()).slice(2)}`;
    });
    return {
      backgroundColor: 'transparent',
      animation: months.length < 60,
      grid: { left: 8, right: 12, top: 12, bottom: 24, containLabel: true },
      tooltip: {
        trigger: 'axis',
        confine: true,
        axisPointer: { type: 'shadow' },
        backgroundColor: isDark ? 'rgba(12,12,20,0.96)' : 'rgba(255,255,255,0.98)',
        borderWidth: 0,
        textStyle: { color: pal.fg, fontSize: 11 },
        formatter: (params) => {
          const rows = (params || []).filter((p) => p.value > 0).map((p) =>
            `<div style="display:flex;align-items:center;gap:6px;font-size:11px">`
            + `<span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:${p.color}"></span>`
            + `<span style="color:${pal.muted}">${p.seriesName}</span>`
            + `<span style="margin-left:auto;font-family:ui-monospace,monospace;color:${pal.fg}">${money(p.value)}</span></div>`,
          ).join('');
          return `<div style="font-size:10px;color:${pal.muted};margin-bottom:4px">${params?.[0]?.axisValue || ''}</div>${rows || '—'}`;
        },
      },
      xAxis: {
        type: 'category',
        data: labels,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: pal.grid } },
        axisLabel: { color: pal.muted, fontSize: 10, hideOverlap: true },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: pal.grid } },
        axisLabel: { color: pal.muted, fontSize: 10, formatter: (val) => money(val) },
      },
      series: [
        {
          name: t('portfolio.fixedIncome.couponLabel'),
          type: 'bar',
          stack: 'cashflow',
          cursor: 'pointer',
          data: months.map((m) => m.coupon),
          itemStyle: { color: COUPON_COLOR },
          barMaxWidth: 28,
        },
        {
          name: t('portfolio.fixedIncome.redemption'),
          type: 'bar',
          stack: 'cashflow',
          cursor: 'pointer',
          data: months.map((m) => m.redemption),
          itemStyle: { color: REDEMPTION_COLOR, borderRadius: [3, 3, 0, 0] },
          barMaxWidth: 28,
        },
      ],
    };
  }, [months, isDark, t]);

  return (
    <motion.div variants={cardVariants} initial="hidden" animate="show">
      <Card variant="elevated" radius="2xl" padding="none" backdropBlur className="group">
        <div
          className="pointer-events-none absolute -top-20 -right-20 w-44 h-44 rounded-full blur-[80px] opacity-0 group-hover:opacity-60 transition-opacity duration-500"
          style={{ background: `radial-gradient(circle, ${COUPON_COLOR}20 0%, transparent 70%)` }}
        />
        <div className="flex items-center justify-between p-4 sm:p-5 pb-0 gap-3 flex-wrap">
          <div className="flex items-center gap-3 min-w-0">
            <span
              className="flex items-center justify-center w-10 h-10 rounded-xl transition-transform duration-300 group-hover:scale-105"
              style={{ backgroundColor: `${COUPON_COLOR}15`, boxShadow: `0 0 20px ${COUPON_COLOR}10` }}
            >
              <CalendarRange className="h-4 w-4 text-success" />
            </span>
            <div className="min-w-0">
              <p className="text-sm font-bold text-fg">{t('portfolio.fixedIncome.cashflowTitle')}</p>
              <p className="text-[11px] text-fg-muted">{t('portfolio.fixedIncome.cashflowSubtitle')}</p>
            </div>
          </div>
          <div className="flex items-center gap-3 text-[11px] text-fg-muted">
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-sm" style={{ background: COUPON_COLOR }} />
              {t('portfolio.fixedIncome.couponLabel')}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-sm" style={{ background: REDEMPTION_COLOR }} />
              {t('portfolio.fixedIncome.redemption')}
            </span>
          </div>
        </div>
        <div className="relative min-h-[200px] px-2 pt-4">
          {option ? (
            <ReactECharts
              key={isDark}
              option={option}
              notMerge
              lazyUpdate
              onEvents={{ click: (p) => setSelectedIdx((prev) => (prev === p.dataIndex ? null : p.dataIndex)) }}
              style={{ height: 'min(40vh, 280px)', minHeight: 200, width: '100%' }}
              opts={{ renderer: 'canvas' }}
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-[200px] gap-3 text-center px-4">
              <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-success/10 ring-1 ring-inset ring-success/20">
                <CalendarRange className="h-6 w-6 text-success" />
              </span>
              <div className="space-y-1">
                <p className="text-sm font-medium text-fg-muted">{t('portfolio.fixedIncome.cashflowEmpty')}</p>
                <p className="text-[11px] text-fg-subtle">{t('portfolio.fixedIncome.cashflowEmptyHint')}</p>
              </div>
            </div>
          )}
        </div>
        {months && (
          <div className="px-3 sm:px-4 pb-4">
            {selected ? (
              <motion.div
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                className="rounded-xl border border-border-default bg-bg-base/60 overflow-hidden"
              >
                <div className="flex items-center justify-between gap-2 px-3 py-2 border-b border-border-default">
                  <p className="text-xs font-bold text-fg truncate">
                    {t('portfolio.fixedIncome.cashflowMonthPayments', { month: bucketLabel(selected) })}
                  </p>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="font-mono text-xs font-bold text-fg">{money(selected.coupon + selected.redemption)}</span>
                    <button
                      onClick={() => setSelectedIdx(null)}
                      aria-label={t('common.close')}
                      className="flex items-center justify-center w-6 h-6 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
                <ul className="max-h-44 overflow-y-auto divide-y divide-border-default">
                  {selected.items.map((it, i) => {
                    const meta = KIND_META[it.kind] || KIND_META.coupon;
                    const Icon = meta.Icon;
                    return (
                      <li key={`${it.name}-${it.ts}-${i}`} className="flex items-center gap-2.5 px-3 py-2">
                        <span
                          className="flex items-center justify-center w-7 h-7 rounded-lg shrink-0"
                          style={{ backgroundColor: `${meta.color}1a`, color: meta.color }}
                        >
                          <Icon className="h-3.5 w-3.5" />
                        </span>
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-fg font-mono truncate">{it.name}</p>
                          <p className="text-[10px] text-fg-muted">{t(meta.labelKey)} · {itemDate(it.ts)}</p>
                        </div>
                        <span className="ml-auto font-mono text-xs font-bold shrink-0" style={{ color: meta.color }}>
                          {money(it.amount)}
                        </span>
                      </li>
                    );
                  })}
                </ul>
              </motion.div>
            ) : (
              <p className="text-center text-[11px] text-fg-subtle py-1.5">
                {t('portfolio.fixedIncome.cashflowClickHint')}
              </p>
            )}
          </div>
        )}
      </Card>
    </motion.div>
  );
}
