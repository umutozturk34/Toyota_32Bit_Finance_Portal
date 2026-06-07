import { memo, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Trophy, ChevronRight } from 'lucide-react';
import { getChangeClass, changeColors, formatPercentSmart } from '../../../shared/utils/formatters';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import Card from '../../../shared/components/card';
import { useMacroIndicators } from '../../macro/hooks/useMacroIndicators';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';

const ANALYTICS_TYPE_TO_MARKET = {
  SPOT: 'STOCK',
  STOCK: 'STOCK',
  CRYPTO: 'CRYPTO',
  FOREX: 'FOREX',
  FUND: 'FUND',
  COMMODITY: 'COMMODITY',
  VIOP: 'VIOP',
};

// Compare expects fully-qualified market types; the analytics-side codes the beater widget
// serialises (SPOT/DEPOSIT/...) must be remapped before they ride along as Compare series.
const ANALYTICS_TO_MARKET_TYPE = {
  SPOT: 'STOCK',
  CRYPTO: 'CRYPTO',
  FOREX: 'FOREX',
  FUND: 'FUND',
  COMMODITY: 'COMMODITY',
  VIOP: 'VIOP',
  BOND: 'BOND',
  DEPOSIT: 'MACRO_DEPOSIT',
};

// A benchmark's macro category decides which MACRO_* series Compare must draw it as.
const MACRO_CATEGORY_TO_MARKET_TYPE = {
  DEPOSIT: 'MACRO_DEPOSIT',
  INFLATION: 'MACRO_INFLATION',
  RATES: 'MACRO_RATE',
};

function shortLabel(code) {
  return (code || '').replace('.IS', '');
}

function BeaterRow({ entry, rank, t, onNavigate }) {
  const marketKey = ANALYTICS_TYPE_TO_MARKET[entry.type] || 'CASH';
  const color = ASSET_TYPE_COLORS[marketKey] || '#6366f1';
  const cls = getChangeClass(entry.nominalReturnPct);
  const excessCls = getChangeClass(entry.excessReturnPct);
  const displayName = instrumentDisplayName(t, entry.type, entry.code, entry.name);
  const initials = shortLabel(entry.code).slice(0, 2).toUpperCase();
  return (
    <button
      type="button"
      onClick={() => onNavigate(entry)}
      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface/60 transition-colors text-left bg-transparent border-none cursor-pointer">
      <span
        className="font-mono text-[10px] font-bold tabular-nums w-5 text-center shrink-0"
        style={{ color }}
      >
        {rank}
      </span>
      <span
        className="w-6 h-6 rounded-full shrink-0 flex items-center justify-center text-[9px] font-bold text-white shadow-sm"
        style={{ backgroundColor: color }}
        aria-hidden
      >
        {initials}
      </span>
      <div className="flex flex-col min-w-0 flex-1">
        <span className="font-display text-[12px] @md:text-sm @xl:text-base font-semibold text-fg truncate leading-tight" title={displayName}>
          {displayName}
        </span>
        <span
          className="font-mono text-[9px] uppercase tracking-[0.14em] leading-tight"
          style={{ color: `${color}cc` }}
        >
          {t(`assets.labels.${entry.type}`, { defaultValue: entry.type })}
        </span>
      </div>
      <div className="flex flex-col items-end shrink-0 gap-0.5">
        <span
          className={`font-mono text-[11px] font-bold tabular-nums leading-tight px-1.5 py-0.5 rounded-md ${changeColors[cls]} bg-surface/50`}
        >
          {formatPercentSmart(entry.nominalReturnPct)}
        </span>
        {entry.excessReturnPct != null && (
          <span
            className={`font-mono text-[9px] font-semibold tabular-nums leading-tight ${changeColors[excessCls]}`}
            title={t('beatersSection.excessTooltip', { defaultValue: 'Excess over benchmark' })}
          >
            {entry.beatsBenchmark ? '▲' : '▼'} {formatPercentSmart(entry.excessReturnPct)}
          </span>
        )}
      </div>
    </button>
  );
}

/**
 * @typedef {Object} BeatersSectionProps
 * @property {{benchmarkCode: string, benchmarkLabel: string, benchmarkReturnPct: number|string|null, period: string, comparisonCurrency: string|null, entries: Array<Object>}|null} data
 */

/** @param {BeatersSectionProps} props */
function BeatersSectionImpl({ data }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { data: macroList = [] } = useMacroIndicators();
  const entries = useMemo(() => data?.entries ?? [], [data]);

  const benchmarkCode = data?.benchmarkCode ?? '';
  const benchmarkLabel = data?.benchmarkLabel || benchmarkCode || t('beatersSection.benchmarkFallback', { defaultValue: 'gösterge' });
  const benchmarkReturn = data?.benchmarkReturnPct;
  const period = data?.period ?? '1Y';
  const comparisonCurrency = data?.comparisonCurrency;
  const benchmarkCls = getChangeClass(benchmarkReturn);

  // Open the clicked beater in Compare framed in the BEATER's comparison currency: the entry is the
  // first series, the benchmark (drawn as its derived MACRO_* type) the second, period -> range, and
  // comparisonCurrency rides along so a USD/EUR-deposit beater opens USD/EUR-basis (not the TRY fallback).
  const handleEntryClick = (entry) => {
    const benchmarkInd = benchmarkCode
      ? macroList.find((m) => m.code === benchmarkCode)
      : null;
    const benchmarkType = benchmarkInd
      ? MACRO_CATEGORY_TO_MARKET_TYPE[benchmarkInd.category] || 'MACRO_RATE'
      : 'MACRO_INFLATION';
    const codes = [entry.code];
    const types = [ANALYTICS_TO_MARKET_TYPE[entry.type] || entry.type];
    if (benchmarkCode) {
      codes.push(benchmarkCode);
      types.push(benchmarkType);
    }
    const params = new URLSearchParams({
      tab: 'compare',
      codes: codes.join(','),
      types: types.join(','),
      range: period,
      // 'overview' (not 'beaters') so Compare's back button returns to the market-overview home where this
      // widget lives, instead of the standalone beater page the user never opened.
      from: 'overview',
    });
    if (comparisonCurrency) params.set('currency', comparisonCurrency);
    navigate(`/analytics?${params.toString()}`);
  };

  return (
    <Card as="section" accentBar="#facc15" radius="xl" padding="none" className="group h-full flex flex-col">
      <button
        type="button"
        onClick={() => navigate('/analytics?tab=beaters')}
        className="flex items-center gap-2 w-full p-3 cursor-pointer hover:bg-surface/30 transition-colors group/title bg-transparent border-x-0 border-t-0 border-b border-border-default shrink-0"
      >
        <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-amber-400/15 shadow-[0_0_16px_-4px_rgba(250,204,21,0.6)]">
          <Trophy className="h-3.5 w-3.5 text-amber-400" />
        </span>
        <div className="flex flex-col items-start min-w-0 flex-1">
          <span className="font-display text-[13px] font-bold text-fg truncate leading-tight">
            {t('beatersSection.title', { defaultValue: 'Benchmark Beaters' })}
          </span>
          <span className="font-mono text-[9px] uppercase tracking-[0.16em] text-fg-subtle leading-tight truncate">
            {benchmarkLabel}
            <span className="mx-1 text-fg-faint">·</span>
            <span className={changeColors[benchmarkCls]}>{formatPercentSmart(benchmarkReturn)}</span>
            <span className="mx-1 text-fg-faint">·</span>
            {period}
            {comparisonCurrency && (
              <>
                <span className="mx-1 text-fg-faint">·</span>
                {comparisonCurrency}
              </>
            )}
          </span>
        </div>
        <ChevronRight className="h-3.5 w-3.5 text-fg-subtle ml-auto opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
      </button>
      <div className="p-2 flex-1 min-h-0 overflow-y-auto scrollbar-auto-hide">
        {entries.length === 0
          ? <p className="text-[11px] text-fg-subtle py-5 text-center">
              {t('beatersSection.empty', { defaultValue: 'Henüz veri yok' })}
            </p>
          : <div className="space-y-0.5">
              {entries.map((entry, idx) => (
                <BeaterRow key={`${entry.type}-${entry.code}`} entry={entry} rank={idx + 1} t={t} onNavigate={handleEntryClick} />
              ))}
            </div>
        }
      </div>
      <button
        type="button"
        onClick={() => navigate('/analytics?tab=beaters')}
        className="flex items-center justify-center gap-1 w-full px-3 py-2 cursor-pointer hover:bg-amber-400/5 transition-colors bg-transparent border-x-0 border-b-0 border-t border-border-default text-amber-400 hover:text-amber-300 shrink-0"
      >
        <span className="font-display text-[11px] font-semibold">
          {t('beatersSection.viewAll', { defaultValue: 'Tümünü gör' })}
        </span>
        <ChevronRight className="h-3 w-3" />
      </button>
    </Card>
  );
}

export default memo(BeatersSectionImpl);
