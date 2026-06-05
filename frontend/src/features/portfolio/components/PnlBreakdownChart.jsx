import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Layers } from 'lucide-react';
import useChartRange from '../../../shared/hooks/useChartRange';
import { useMoney } from '../../../shared/hooks/useMoney';
import { useRateHistory } from '../../../shared/hooks/useRateHistory';
import { usePortfolioPerformance } from '../hooks/usePortfolioData';
import { ASSET_TYPE_FILTERS, ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import Card from '../../../shared/components/card';
import PnlTimeSeriesChart from './PnlTimeSeriesChart';

const SERIES_COLORS = {
  total: '#6366f1',
  open: '#10b981',
  closed: '#f59e0b',
};

function PnlBreakdownChart({ portfolioId }) {
  const { t } = useTranslation();
  const { format: money, formatCompact: moneyCompact, currency } = useMoney();
  const { convertAt } = useRateHistory();
  // The aggregate chart shows USD/EUR or (ORIGINAL/TRY → TRY); convert each PnL component into that frame.
  const frameBase = currency === 'USD' || currency === 'EUR' ? currency : 'TRY';
  const [range, setRange] = useChartRange();
  const [assetType, setAssetType] = useState(null);
  const [valueMode, setValueMode] = useState('money');
  const { data: perfData = [], isLoading } = usePortfolioPerformance(portfolioId, range, assetType);
  // Spinner only on first load; a background refetch keeps the cached chart visible (no blank-then-redraw flash).
  const loading = isLoading;

  // TL mode: Total / Open / Closed P&L (the backend folds the filtered type's realized in too).
  // Percent mode + headline %: cost-based cumulative return (P&L ÷ invested cost) — "return on the total
  // capital deployed", i.e. the honest "what did my money do" for the portfolio's OWN return. Adding a lot
  // grows the cost, so the % dilutes (a big lot can drop it sharply) — that is intentional/correct here.
  // TWR (lot-immune) is reserved for the COMPARE chart, where contribution-immunity is needed for a fair
  // benchmark comparison; the portfolio's own return graphs (this + Performance) stay cost-based.
  // PnL in a non-TRY frame = value@date − entry-date-FX cost, supplied PER POINT by the backend
  // (costBasisByCcy / valueByCcy on the point AND each detail) — the only source that knows each lot's true
  // entry-date FX regardless of window/filter. Open PnL = valueByCcy − costBasisByCcy; closed = realized
  // converted per-date; total = open + closed. TRY frame uses the backend TRY scalars directly.
  const { series, contributors } = useMemo(() => {
    const sorted = [...perfData].sort((a, b) => a.time - b.time);
    const isTry = frameBase === 'TRY';
    const total = []; const open = []; const closed = []; const ret = [];
    let lastDetails = []; let lastCash = 0; let lastDateStr = null;
    for (const p of sorted) {
      const dateStr = new Date(p.time).toLocaleDateString('sv-SE');
      let openV; let cashV; let totalV; let pct; let openValueDisp;
      if (isTry) {
        openV = Number(p.open); cashV = Number(p.cash); totalV = Number(p.pnl);
        pct = Number(p.pnlPercent); openValueDisp = Number(p.value);
      } else {
        const valueCcy = p.valueByCcy?.[frameBase];
        const costCcy = p.costBasisByCcy?.[frameBase];
        const realizedCcy = p.realizedByCcy?.[frameBase];
        const pnlCcy = p.pnlByCcy?.[frameBase];
        // Total PnL = pnlByCcy (open + realized, closed locked at exit FX), independent of whether the
        // displayed value carries closed proceeds. Closed slice = realizedByCcy; open = total − closed.
        // Falls back to value − cost then a per-date TRY conversion. Never (value − cost) + cash.
        totalV = pnlCcy ?? ((valueCcy != null && costCcy != null) ? valueCcy - costCcy : (Number(convertAt(p.pnl, 'TRY', dateStr)) || 0));
        cashV = realizedCcy != null ? realizedCcy : (Number(convertAt(p.cash, 'TRY', dateStr)) || 0);
        openV = totalV - cashV;
        pct = costCcy ? (totalV / Math.abs(costCcy)) * 100 : Number(p.pnlPercent);
        openValueDisp = valueCcy ?? null;
      }
      total.push({ time: p.time, valueTry: totalV, priceTry: openValueDisp, pct });
      open.push({ time: p.time, valueTry: openV });
      closed.push({ time: p.time, valueTry: cashV });
      ret.push({ time: p.time, valueTry: pct });
      lastDetails = p.details || []; lastCash = cashV; lastDateStr = dateStr;
    }
    const seriesArr = valueMode === 'percent'
      ? [{ key: 'return', name: t('portfolio.pnlBreakdown.return', { defaultValue: 'Getiri' }), color: SERIES_COLORS.total, points: ret }]
      : [
          { key: 'total', name: t('portfolio.pnlBreakdown.total'), color: SERIES_COLORS.total, points: total },
          { key: 'open', name: t('portfolio.pnlBreakdown.open'), color: SERIES_COLORS.open, points: open },
          { key: 'closed', name: t('portfolio.pnlBreakdown.closed'), color: SERIES_COLORS.closed, points: closed },
        ];
    const list = lastDetails.map((d) => {
      const isOther = d.label === 'OTHER';
      const label = isOther
        ? t('portfolio.allocation.otherLabel')
        : (d.label !== d.assetType ? d.label : t(`assets.labels.${d.assetType}`, { defaultValue: d.assetType }));
      let pnl;
      if (isTry) {
        pnl = Number(d.pnlTry);
      } else {
        // Per-currency DIRECTION-AWARE PnL frame from the backend (cost @ entry-FX, value @ point-FX, with the
        // open-VIOP SHORT sign correction baked into value). One rule for every type: this nets a VIOP hedge and
        // prices an open VIOP leg per-date, instead of the old branch that converted the direction-blind notional
        // at TODAY's FX (the wrong huge VIOP contribution). Falls back to converting the TRY scalar at the point's
        // date only when the frame is absent (e.g. the closed-positions slice carries no per-type footprints) —
        // else the formatter would treat the TRY number as already in frameBase and mis-scale it by the FX rate.
        const framePnl = d.pnlByCcy?.[frameBase];
        pnl = (framePnl != null)
          ? Number(framePnl)
          : (Number(convertAt(d.pnlTry, 'TRY', lastDateStr)) || 0);
      }
      return {
        key: d.label, label,
        color: isOther ? '#7d8590' : (ASSET_TYPE_COLORS[d.assetType] || '#6366f1'),
        pnl: pnl || 0, value: Number(d.valueTry) || 0,
      };
    });
    if (lastCash !== 0) {
      list.push({
        key: '__closed__',
        label: t('portfolio.pnlBreakdown.closed', { defaultValue: 'Kapalı Pozisyonlar' }),
        color: SERIES_COLORS.closed, pnl: lastCash, value: 0,
      });
    }
    return { series: seriesArr, contributors: list.sort((a, b) => b.pnl - a.pnl) };
  }, [perfData, valueMode, t, convertAt, frameBase]);

  const filterPills = (
    <div className="flex gap-0.5 overflow-x-auto rounded-xl border border-border-default bg-bg-base p-1">
      {ASSET_TYPE_FILTERS.map(({ id }) => (
        <button
          key={id || 'all'}
          type="button"
          onClick={() => setAssetType(id)}
          className={`shrink-0 rounded-lg px-2.5 py-1.5 text-[11px] font-semibold border-none cursor-pointer bg-transparent ${assetType === id ? 'text-accent bg-accent/15' : 'text-fg-muted hover:text-fg'}`}
        >
          {id ? t(`assets.labels.${id}`, { defaultValue: id }) : t('assets.labels.ALL', { defaultValue: 'Tümü' })}
        </button>
      ))}
    </div>
  );

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[1fr_300px] gap-4">
      <div className="min-w-0">
        <PnlTimeSeriesChart
          series={series}
          loading={loading}
          valueMode={valueMode}
          preConverted
          showSummary
          range={range}
          onRangeChange={setRange}
          title={t('portfolio.pnlBreakdown.title')}
          Icon={Layers}
          headerExtra={(
            <div className="flex items-center gap-2 flex-wrap">
              {filterPills}
              <div className="flex gap-0.5 rounded-xl border border-border-default bg-bg-base p-1 shrink-0">
                {[
                  { id: 'money', label: t('portfolio.pnlBreakdown.modeValue', { defaultValue: 'Tutar' }) },
                  { id: 'percent', label: t('portfolio.pnlBreakdown.modePercent', { defaultValue: '% Getiri' }) },
                ].map((m) => (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => setValueMode(m.id)}
                    className={`shrink-0 rounded-lg px-2.5 py-1.5 text-[11px] font-semibold border-none cursor-pointer bg-transparent ${valueMode === m.id ? 'text-accent bg-accent/15' : 'text-fg-muted hover:text-fg'}`}
                  >
                    {m.label}
                  </button>
                ))}
              </div>
            </div>
          )}
          emptyLabel={t('portfolio.pnlBreakdown.empty')}
        />
      </div>

      <Card variant="elevated" radius="2xl" padding="lg" backdropBlur className="self-start lg:max-h-[480px] lg:overflow-y-auto">
        <p className="text-xs font-bold text-fg mb-3">{t('portfolio.pnlBreakdown.contributors', { defaultValue: 'K/Z Katkısı' })}</p>
        {contributors.length === 0 ? (
          <p className="text-xs text-fg-muted">{t('portfolio.pnlBreakdown.empty')}</p>
        ) : (
          <ul className="space-y-2.5">
            {contributors.map((c) => (
              <li key={c.key} className="flex items-center justify-between gap-2">
                <span className="flex items-center gap-2 min-w-0">
                  <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: c.color }} />
                  <span className="text-xs text-fg truncate">{c.label}</span>
                </span>
                <span className={`text-xs font-mono font-semibold shrink-0 ${c.pnl >= 0 ? 'text-success' : 'text-danger'}`} title={money(c.pnl, frameBase)}>
                  {c.pnl >= 0 ? '+' : ''}{moneyCompact(c.pnl, frameBase)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

export default PnlBreakdownChart;
