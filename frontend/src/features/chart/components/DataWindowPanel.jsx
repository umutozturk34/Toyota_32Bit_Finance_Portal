import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { ScanSearch, CalendarDays, TrendingUp, TrendingDown } from 'lucide-react';
import useAppStore from '../../../shared/stores/useAppStore';
import { priceDecimals, formatPrice, formatPercent, formatVolume } from '../../../shared/utils/formatters';
import { analyzeSeries, analyzeAt } from '../lib/chartAnalytics';
import MetricCard from './MetricCard';
import PositionBar from './PositionBar';
import RiskMeter from './RiskMeter';

const CCY_SYMBOL = { TRY: '₺', USD: '$', EUR: '€' };

function toneClass(v) {
  if (v == null) return 'text-fg';
  return v > 0 ? 'text-success' : v < 0 ? 'text-danger' : 'text-fg-muted';
}

function toneAccent(v) {
  if (v == null) return 'var(--color-border-default)';
  return v > 0 ? 'var(--color-success)' : v < 0 ? 'var(--color-danger)' : 'var(--color-border-default)';
}

function Section({ label, children }) {
  return (
    <div className="space-y-1.5">
      <div className="text-[10px] font-display font-bold uppercase tracking-[0.16em] text-fg-muted">{label}</div>
      {children}
    </div>
  );
}

function DetailRow({ label, children }) {
  return (
    <div className="flex items-center justify-between gap-2 py-0.5 border-t border-border-default/30 first:border-t-0">
      <span className="text-[11px] text-fg-muted truncate shrink min-w-0">{label}</span>
      <span className="font-mono text-xs tabular-nums text-right text-fg min-w-0 truncate" title={typeof children === 'string' ? children : undefined}>{children}</span>
    </div>
  );
}

/**
 * "Mercek" (Lens) — the chart's analysis cockpit, split into two surfaces so the most-glanced summary sits ABOVE
 * the chart while the deep analytics stay in the right column:
 *   variant="summary"   → thin top strip: brand/date + CLOSE hero + range position (hover-reactive).
 *   variant="analytics" → right panel: performance, risk/volatility, day detail (series + per-day).
 * Calls the single analyze() facade once (memoized per data change; only the light per-index work re-runs on
 * hover). Candles arrive ALREADY in the display currency (per FX date), so values/percentages are correct.
 */
export default function DataWindowPanel({ candles, hover, assetType, variant = 'analytics' }) {
  const { t, i18n } = useTranslation();
  const displayCurrency = useAppStore((s) => s.displayCurrency) || 'TRY';
  const locale = i18n.language?.startsWith('tr') ? 'tr-TR' : 'en-US';
  const symbol = CCY_SYMBOL[displayCurrency] || '';

  const n = candles?.length ?? 0;
  const hovering = hover?.index != null && hover.index >= 0 && hover.index < n;
  const series = useMemo(() => analyzeSeries(candles, assetType), [candles, assetType]);
  const a = useMemo(
    () => analyzeAt(candles, hovering ? hover.index : null, series),
    [candles, hovering, hover?.index, series],
  );

  if (!a) return null;

  const money = (v) => (v == null ? '—' : `${symbol}${formatPrice(v, { locale, minDecimals: 0, maxDecimals: priceDecimals(v) })}`);
  const pct1 = (v) => (v == null ? '—' : `${v.toLocaleString(locale, { maximumFractionDigits: 1 })}%`);
  const compactPct = (v, signed = true) => {
    if (v == null) return '—';
    const abs = Math.abs(v);
    if (abs >= 10000) {
      const c = new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(abs);
      return `${signed ? (v < 0 ? '−' : '+') : ''}${c}%`;
    }
    return signed ? formatPercent(v, locale) : pct1(abs);
  };
  const dateLabel = a.date
    ? new Date(a.date).toLocaleDateString(locale, { day: '2-digit', month: 'short', year: 'numeric' })
    : '—';
  const f = a.fields;

  // ─── Top summary strip: brand/date · CLOSE hero · range (stretches to fill) ───
  if (variant === 'summary') {
    return (
      <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-6 px-3 sm:px-4 py-2.5 sm:pr-12 sm:overflow-x-auto sm:scrollbar-hide overscroll-x-contain">
        <div className="shrink-0 flex flex-col justify-center gap-1 w-full sm:w-[88px]">
          <span className="inline-flex items-center gap-1.5 text-xs font-display font-bold uppercase tracking-[0.16em] text-fg leading-none">
            <ScanSearch className="h-4 w-4 text-accent" />
            {t('chart.dataWindow.title')}
          </span>
          <span className={`inline-flex items-center gap-1 text-[10px] font-mono whitespace-nowrap leading-none ${hovering ? 'text-accent' : 'text-fg-subtle'}`}>
            <CalendarDays className="h-3 w-3" />
            {dateLabel}
          </span>
        </div>

        <div className="relative shrink-0 w-full sm:w-[208px] min-w-0 sm:overflow-hidden flex flex-col justify-center gap-1 pt-2 pl-2.5 sm:pt-0 sm:pl-4 border-t sm:border-t-0 sm:border-l border-border-default/60">
          {/* Solid left accent bar instead of an inset box-shadow — the shadow sub-pixel-breaks (gaps/doubling)
              at fractional browser zoom; a filled element renders crisply at every zoom level. */}
          <span aria-hidden="true" className="absolute left-0 inset-y-0 w-0.5" style={{ background: toneAccent(a.daily?.percent) }} />
          <span className="text-[9px] font-mono uppercase tracking-wider text-fg-subtle leading-none">{t('chart.dataWindow.close')}</span>
          {/* justify-between pins the % badge to the block's RIGHT edge and the price to the LEFT edge, so hovering
              a different day (a wider/narrower value) never slides them: the price grows from a fixed left, the
              badge from a fixed right. tabular-nums keeps digit widths equal; truncate + the fixed-width block clip
              rather than reflow. This is what stops the strip "jumping" as you move across the chart. */}
          <span className="flex items-center justify-between gap-2.5 leading-none">
            <span title={money(a.close)} className="font-mono text-lg sm:text-xl font-bold tabular-nums text-fg min-w-0 truncate">{money(a.close)}</span>
            {a.daily?.percent != null && (
              <span
                className={`shrink-0 inline-flex items-center gap-0.5 rounded-full border px-1.5 py-0.5 font-mono text-[11px] font-bold tabular-nums ${toneClass(a.daily.percent)}`}
                style={{ borderColor: `${toneAccent(a.daily.percent)}55`, background: `${toneAccent(a.daily.percent)}1a` }}
              >
                {a.daily.percent >= 0 ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
                {formatPercent(a.daily.percent, locale)}
              </span>
            )}
          </span>
          {/* Always render this sub-line (reserving its height) so the block doesn't grow/shrink on the first
              candle. justify-between + compact money keep the change and prev-close anchored to opposite ends. */}
          <span className="flex items-center justify-between gap-2 font-mono text-[10px] leading-none min-h-[12px]">
            {a.daily?.value != null && (
              <span className={`shrink-0 ${toneClass(a.daily.value)}`}>{a.daily.value >= 0 ? '+' : '−'}{money(Math.abs(a.daily.value))}</span>
            )}
            {a.prevClose != null && (
              <span className="truncate min-w-0 text-fg-subtle">{t('chart.dataWindow.prevClose')} <span className="text-fg-muted">{money(a.prevClose)}</span></span>
            )}
          </span>
        </div>

        {a.hiLo?.positionPct != null && (
          <div className="w-full sm:ml-auto sm:w-48 lg:w-56 sm:shrink-0 pt-2 pl-0 sm:pt-0 sm:pl-4 border-t sm:border-t-0 sm:border-l border-border-default/60">
            <PositionBar pct={a.hiLo.positionPct} lowLabel={money(a.hiLo.low)} highLabel={money(a.hiLo.high)} />
          </div>
        )}
      </div>
    );
  }

  // ─── Right analytics panel: performance · risk/volatility · day detail ───
  const hasDetail = f.hasRealOHL || f.sellingPrice != null || f.bulletinPrice != null
    || (f.investorCount != null && f.investorCount > 0) || (f.portfolioSize != null && f.portfolioSize > 0)
    || (f.volume != null && f.volume > 0);

  return (
    <div className="p-3 sm:p-4 space-y-3">
      <Section label={t('chart.dataWindow.sectionPerformance')}>
        <div className="grid grid-cols-1 min-[400px]:grid-cols-2 gap-1.5">
          {a.period?.percent != null && (
            <MetricCard label={t('chart.dataWindow.periodReturn')} value={compactPct(a.period.percent)} tone={toneClass(a.period.percent)} accent={toneAccent(a.period.percent)} />
          )}
          {a.drawdown?.percent != null && a.drawdown.percent < 0 && (
            <MetricCard label={t('chart.dataWindow.drawdown')} value={formatPercent(a.drawdown.percent, locale)} tone="text-danger" accent="var(--color-danger)" />
          )}
          {a.range?.percent != null && (
            <MetricCard label={t('chart.dataWindow.range')} value={compactPct(a.range.percent, false)} />
          )}
          {a.streak !== 0 && (
            <MetricCard label={t('chart.dataWindow.streak')} value={`${a.streak > 0 ? '+' : ''}${a.streak} ${t('chart.dataWindow.streakUnit')}`} tone={toneClass(a.streak)} accent={toneAccent(a.streak)} />
          )}
          {a.sma20?.distancePct != null && (
            <MetricCard label={t('chart.dataWindow.smaDistance', { n: 20 })} value={compactPct(a.sma20.distancePct)} tone={toneClass(a.sma20.distancePct)} accent={toneAccent(a.sma20.distancePct)} />
          )}
          {a.sma50?.distancePct != null && (
            <MetricCard label={t('chart.dataWindow.smaDistance', { n: 50 })} value={compactPct(a.sma50.distancePct)} tone={toneClass(a.sma50.distancePct)} accent={toneAccent(a.sma50.distancePct)} />
          )}
        </div>
      </Section>

      {(a.volatility != null || a.atr?.percent != null || a.risk) && (
        <Section label={t('chart.dataWindow.sectionRisk')}>
          <RiskMeter band={a.risk} vol={a.volatility} t={t} locale={locale} />
          {a.atr?.percent != null && (
            <div className="grid grid-cols-2 gap-1.5">
              <MetricCard label={t('chart.dataWindow.atr')} value={`${a.atr.percent.toLocaleString(locale, { maximumFractionDigits: 2 })}%`} />
            </div>
          )}
        </Section>
      )}

      {hasDetail && (
        <Section label={t('chart.dataWindow.sectionDetail')}>
          <div>
            {f.hasRealOHL && (
              <div className="border-t border-border-default/30 py-1 first:border-t-0">
                <div className="mb-0.5 text-[11px] text-fg-muted">O / H / L / C</div>
                <div className="grid grid-cols-2 gap-x-3 gap-y-0.5 font-mono text-xs tabular-nums">
                  <span className="text-fg-muted">O <span className="text-fg">{money(f.open)}</span></span>
                  <span className="text-fg-muted">H <span className="text-fg">{money(f.high)}</span></span>
                  <span className="text-fg-muted">L <span className="text-fg">{money(f.low)}</span></span>
                  <span className="text-fg-muted">C <span className="text-fg">{money(a.close)}</span></span>
                </div>
              </div>
            )}
            {f.sellingPrice != null && (
              <DetailRow label={t('chart.toolbar.crosshair.sell')}>{money(f.sellingPrice)}</DetailRow>
            )}
            {f.buyingPrice != null && (
              <DetailRow label={t('chart.toolbar.crosshair.buy')}>{money(f.buyingPrice)}</DetailRow>
            )}
            {f.spread?.value != null && (
              <DetailRow label={t('chart.dataWindow.spread')}>{money(f.spread.value)}</DetailRow>
            )}
            {f.effectiveSellingPrice != null && f.effectiveSellingPrice !== f.sellingPrice && (
              <DetailRow label={t('chart.toolbar.crosshair.effSell')}>{money(f.effectiveSellingPrice)}</DetailRow>
            )}
            {f.effectiveBuyingPrice != null && f.effectiveBuyingPrice !== f.buyingPrice && (
              <DetailRow label={t('chart.toolbar.crosshair.effBuy')}>{money(f.effectiveBuyingPrice)}</DetailRow>
            )}
            {f.bulletinPrice != null && (
              <DetailRow label={t('chart.toolbar.crosshair.bulletin')}>{money(f.bulletinPrice)}</DetailRow>
            )}
            {f.investorCount != null && f.investorCount > 0 && (
              <DetailRow label={t('lightweightChart.investorCount')}>
                {f.investorCount.toLocaleString(locale, { maximumFractionDigits: 0 })}
              </DetailRow>
            )}
            {f.portfolioSize != null && f.portfolioSize > 0 && (
              <DetailRow label={t('lightweightChart.portfolioSize')}>{formatVolume(f.portfolioSize)}</DetailRow>
            )}
            {f.volume != null && f.volume > 0 && (
              <DetailRow label={t('chart.dataWindow.volume')}>
                {formatVolume(f.volume)}
                {a.avgVolume != null && (
                  <span className="ml-1 text-fg-subtle">· {t('chart.dataWindow.avgShort')} {formatVolume(a.avgVolume)}</span>
                )}
              </DetailRow>
            )}
          </div>
        </Section>
      )}

      <p className="text-[10px] font-mono leading-snug text-fg-subtle">
        {hovering ? t('chart.dataWindow.hovering') : t('chart.dataWindow.idleHint')}
      </p>
    </div>
  );
}
