import { useMemo } from 'react';
import { TrendingUp, TrendingDown, Flame, Trophy } from 'lucide-react';
import HeroStat from '../HeroStat';
import { isRateLike, seriesWindowPct } from '../../lib/compareSeriesUtils';
import { realExcessPct } from '../../utils';
import { fitMoney, formatPercentSmart } from '../../../../shared/utils/formatters';
import useComparePeriodInflation from '../../hooks/useComparePeriodInflation';

const GREEN = '#10b981';
const RED = '#ef4444';
const AMBER = '#f59e0b';
const GREY = '#6b7280';

const COLS = { 1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3' };

// Plain-words verdict above the Compare chart: turns the lines into "this much gained, period inflation was X,
// you beat/fell behind it" using only numbers already on screen (seriesWindowPct — the same headline the info-bar
// and chart line carry, including the Beater-cached override so a Beater click-through matches the table to the
// decimal). The inflation/real columns appear ONLY in a TRY frame: TÜFE deflates the lira, so quoting it against
// a USD/EUR-framed return is meaningless. Real excess uses the geometric Fisher relation (realExcessPct), never
// the arithmetic point-difference, so the verdict agrees with the inflation-beater.
export default function CompareVerdict({
  seriesData = [], targetCurrency, sharedBaselineDate, authoritativeReturns, levelMode, t,
}) {
  const isTry = targetCurrency === 'TRY';

  const endDate = useMemo(() => {
    let max = null;
    for (const s of seriesData) {
      const pts = s.points || [];
      const last = pts.length > 0 ? String(pts[pts.length - 1].date).slice(0, 10) : null;
      if (last && (!max || last > max)) max = last;
    }
    return max || undefined;
  }, [seriesData]);

  // CPI from a selected TÜFE line (Beater hand-off or user-added) wins; otherwise fetch it independently so the
  // period inflation can ALWAYS show — but only in a TRY frame (the hook is disabled elsewhere, no wasted read).
  const cpiSeries = useMemo(
    () => seriesData.find((s) => s.indicator?.type === 'MACRO_INFLATION'),
    [seriesData],
  );
  // The verdict's CPI growth must be PUBLICATION-LAG aware so Compare matches the Beater (which already anchors
  // symmetrically and reads correct). CPI publishes ~1 month late, so the raw chart-series window (seriesWindowPct)
  // pins the baseline at the calendar start and reads ~11 months of index for a "1Y" span — the 30.82-vs-32.61
  // under-report. Priority: the Beater-cached authoritative return when a Beater window is pinned (exact match);
  // else the symmetric-anchor hook (useComparePeriodInflation → true YoY); the raw-series pct is a last resort only.
  const cpiCode = cpiSeries?.indicator?.code;
  const cpiAuthoritative = cpiCode != null ? authoritativeReturns?.[cpiCode] : null;
  const cpiFromSeries = cpiSeries
    ? seriesWindowPct(cpiSeries.indicator, cpiSeries.points, sharedBaselineDate, authoritativeReturns)
    : null;
  const wantFetch = isTry && !levelMode && cpiAuthoritative == null && seriesData.length > 0;
  const fetchedCpi = useComparePeriodInflation({ enabled: wantFetch, baselineDate: sharedBaselineDate, endDate });
  const cpiPct = cpiAuthoritative != null
    ? Number(cpiAuthoritative)
    : (isTry ? (fetchedCpi != null ? fetchedCpi : cpiFromSeries) : null);

  // Comparable = the value lines (assets + portfolio); rate-like indices (CPI/PPI, bond yields) are the
  // benchmark, never a "subject" whose return we headline.
  const comparable = useMemo(() => seriesData
    .filter((s) => s.indicator && !isRateLike(s.indicator.type))
    .map((s) => {
      const pct = seriesWindowPct(s.indicator, s.points, sharedBaselineDate, authoritativeReturns);
      const isPortfolio = s.indicator.type === 'PORTFOLIO';
      let pnl = null;
      if (isPortfolio && s.points && s.points.length > 0) {
        const sorted = [...s.points].sort((a, b) => String(a.date).localeCompare(String(b.date)));
        const lp = sorted[sorted.length - 1];
        pnl = lp && lp.pnlTry != null ? Number(lp.pnlTry) : null;
      }
      return {
        name: s.indicator.displayName || s.indicator.name || s.indicator.code,
        pct, isPortfolio, pnl,
      };
    })
    .filter((s) => s.pct != null), [seriesData, sharedBaselineDate, authoritativeReturns]);

  const sinceLabel = useMemo(() => {
    if (!sharedBaselineDate) return null;
    const d = new Date(`${String(sharedBaselineDate).slice(0, 10)}T00:00:00`);
    if (Number.isNaN(d.getTime())) return null;
    const date = d.toLocaleDateString(t('common.localeTag', { defaultValue: 'tr-TR' }), {
      day: '2-digit', month: 'short', year: 'numeric',
    });
    return t('analytics.compareVerdict.since', { date, defaultValue: `Bu dönem · ${date}'ten bugüne` });
  }, [sharedBaselineDate, t]);

  // The verdict reads a cumulative % growth; in level/Yıllık mode the lines are annual rate LEVELS, so a
  // single "gained X / beat inflation" sentence is undefined — render nothing there.
  if (levelMode || comparable.length === 0) return null;

  // Subject = the portfolio if compared, else a lone asset; with 2+ assets there is no single subject, so the
  // hero shows best/worst instead of one "your return".
  // Subject = the single focus line whose return we headline + judge vs inflation: the portfolio when exactly
  // ONE is compared (even beside assets — it's the user's money), else a lone asset. Two portfolios or several
  // assets have no single subject, so the hero shows best/worst instead.
  const portfolios = comparable.filter((s) => s.isPortfolio);
  const subject = portfolios.length === 1
    ? portfolios[0]
    : (portfolios.length === 0 && comparable.length === 1 ? comparable[0] : null);
  const best = comparable.reduce((a, b) => (b.pct > a.pct ? b : a), comparable[0]);
  const worst = comparable.reduce((a, b) => (b.pct < a.pct ? b : a), comparable[0]);
  const showInflation = isTry && cpiPct != null;

  const stats = [];
  if (subject) {
    const accent = subject.pct > 0 ? GREEN : subject.pct < 0 ? RED : GREY;
    const money = subject.pnl != null
      ? `${subject.pnl > 0 ? '+' : ''}${fitMoney(subject.pnl, { currency: targetCurrency, maxChars: 16 })}`
      : null;
    stats.push({
      key: 'gain', accent,
      icon: subject.pct >= 0 ? <TrendingUp className="h-4 w-4" /> : <TrendingDown className="h-4 w-4" />,
      label: t('analytics.compareVerdict.gain', { defaultValue: 'Getirin' }),
      value: <span style={{ color: accent }}>{formatPercentSmart(subject.pct)}</span>,
      sub: subject.isPortfolio ? (money || subject.name) : subject.name,
    });
  } else if (comparable.length >= 2) {
    stats.push({
      key: 'best', accent: GREEN, icon: <TrendingUp className="h-4 w-4" />,
      label: t('analytics.compareVerdict.best', { defaultValue: 'En iyi' }),
      value: <span className="text-success">{formatPercentSmart(best.pct)}</span>, sub: best.name,
    });
    stats.push({
      key: 'worst', accent: RED, icon: <TrendingDown className="h-4 w-4" />,
      label: t('analytics.compareVerdict.worst', { defaultValue: 'En kötü' }),
      value: <span className="text-danger">{formatPercentSmart(worst.pct)}</span>, sub: worst.name,
    });
  }

  if (showInflation) {
    stats.push({
      key: 'cpi', accent: AMBER, icon: <Flame className="h-4 w-4" />,
      label: t('analytics.cpiGrowth', { defaultValue: 'TÜFE büyümesi (dönemde)' }),
      value: formatPercentSmart(cpiPct),
      sub: t('analytics.compareVerdict.inflationSub', { defaultValue: 'Alım gücü eşiği' }),
    });
    if (subject) {
      const real = realExcessPct(subject.pct, cpiPct);
      if (real != null) {
        const beat = real > 0;
        stats.push({
          key: 'real', accent: beat ? GREEN : RED,
          icon: beat ? <Trophy className="h-4 w-4" /> : <TrendingDown className="h-4 w-4" />,
          label: t('analytics.compareVerdict.real', { defaultValue: 'Reel sonuç' }),
          value: <span className={beat ? 'text-success' : 'text-danger'}>{formatPercentSmart(real)}</span>,
          sub: t(beat ? 'portfolio.fixedIncome.pnl.beat' : 'portfolio.fixedIncome.pnl.behind', {
            defaultValue: beat ? 'Enflasyonu yendin' : 'Enflasyonun altında',
          }),
        });
      }
    }
  }

  if (stats.length === 0) return null;

  return (
    <div className="space-y-2">
      <div className={`grid grid-cols-1 ${COLS[Math.min(stats.length, 3)]} gap-3`}>
        {stats.map((s) => (
          <HeroStat key={s.key} icon={s.icon} label={s.label} value={s.value} sub={s.sub} accent={s.accent} />
        ))}
      </div>
      {sinceLabel && (
        <p className="px-1 text-[11px] font-mono text-fg-subtle leading-snug">{sinceLabel}</p>
      )}
    </div>
  );
}
