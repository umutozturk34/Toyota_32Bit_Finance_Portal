import { memo, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Medal, ChevronRight, ArrowUpRight } from 'lucide-react';
import { getChangeClass, changeColors, formatPercentSmart } from '../../../shared/utils/formatters';
import { ASSET_TYPE_COLORS } from '../../../shared/constants/assetTypes';
import Card from '../../../shared/components/card';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';
import { assetRoute } from '../../watch/lib/watchConstants';

const ACCENT = '#14b8a6';
const CCY_SYMBOL = { TRY: '₺', USD: '$', EUR: '€' };

// The dataset's analytics types (SPOT/...) map to the market type used for colours and the detail route.
const ANALYTICS_TYPE_TO_MARKET = {
  SPOT: 'STOCK',
  CRYPTO: 'CRYPTO',
  FOREX: 'FOREX',
  FUND: 'FUND',
  COMMODITY: 'COMMODITY',
};

function shortLabel(code) {
  return (code || '').replace('.IS', '');
}

function ReturnRow({ entry, rank, t, onNavigate }) {
  const marketKey = ANALYTICS_TYPE_TO_MARKET[entry.type] || 'STOCK';
  const color = ASSET_TYPE_COLORS[marketKey] || '#6366f1';
  const cls = getChangeClass(entry.returnPct);
  const displayName = instrumentDisplayName(t, entry.type, entry.code, entry.name);
  const initials = shortLabel(entry.code).slice(0, 2).toUpperCase();
  const vol = entry.volatility != null ? Number(entry.volatility) : null;
  return (
    <button
      type="button"
      onClick={() => onNavigate(entry)}
      className="group/row w-full flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface/60 transition-colors text-left bg-transparent border-none cursor-pointer">
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
          {formatPercentSmart(entry.returnPct)}
        </span>
        {vol != null && (
          <span
            className="font-mono text-[9px] font-semibold tabular-nums leading-tight text-fg-subtle"
            title={t('returnsSection.volatilityTooltip', { defaultValue: 'Yıllık volatilite' })}
          >
            σ {vol.toLocaleString('tr-TR', { maximumFractionDigits: 1 })}%
          </span>
        )}
      </div>
      <ArrowUpRight className="h-3 w-3 text-fg-subtle ml-0.5 shrink-0 opacity-0 group-hover/row:opacity-100 transition-opacity" />
    </button>
  );
}

/**
 * @typedef {Object} ReturnsSectionProps
 * @property {{period: string, currency: string, asOf: string|null, sortBy: string, entries: Array<Object>}|null} data
 */

/** @param {ReturnsSectionProps} props */
function ReturnsSectionImpl({ data }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const entries = useMemo(() => data?.entries ?? [], [data]);
  const period = data?.period ?? '1Y';
  const currency = data?.currency || 'TRY';

  const handleEntryClick = (entry) => {
    const route = assetRoute(ANALYTICS_TYPE_TO_MARKET[entry.type], entry.code);
    if (route) navigate(route);
  };

  const goToReturns = () => navigate('/market?tab=returns');

  return (
    <Card as="section" accentBar={ACCENT} radius="xl" padding="none" className="group h-full flex flex-col">
      <button
        type="button"
        onClick={goToReturns}
        className="flex items-center gap-2 w-full p-3 cursor-pointer hover:bg-surface/30 transition-colors group/title bg-transparent border-x-0 border-t-0 border-b border-border-default shrink-0"
      >
        <span className="flex items-center justify-center w-7 h-7 rounded-lg" style={{ background: `${ACCENT}26`, boxShadow: `0 0 16px -4px ${ACCENT}99` }}>
          <Medal className="h-3.5 w-3.5" style={{ color: ACCENT }} />
        </span>
        <div className="flex flex-col items-start min-w-0 flex-1">
          <span className="font-display text-[13px] font-bold text-fg truncate leading-tight">
            {t('returnsSection.title', { defaultValue: 'Getiriler' })}
          </span>
          <span className="font-mono text-[9px] uppercase tracking-[0.16em] text-fg-subtle leading-tight truncate">
            {CCY_SYMBOL[currency] || '₺'}
            <span className="mx-1 text-fg-faint">·</span>
            {period}
            <span className="mx-1 text-fg-faint">·</span>
            {t('returnsSection.currencyReturn', { defaultValue: '{{ccy}} getiri', ccy: currency })}
          </span>
        </div>
        <ChevronRight className="h-3.5 w-3.5 text-fg-subtle ml-auto opacity-0 group-hover/title:opacity-100 group-hover/title:translate-x-0.5 transition-all" />
      </button>
      <div className="p-2 flex-1 min-h-0 overflow-y-auto scrollbar-auto-hide">
        {entries.length === 0
          ? <p className="text-[11px] text-fg-subtle py-5 text-center">
              {t('returnsSection.empty', { defaultValue: 'Hazırlanıyor…' })}
            </p>
          : <div className="space-y-0.5">
              {entries.map((entry, idx) => (
                <ReturnRow key={`${entry.type}-${entry.code}`} entry={entry} rank={idx + 1} t={t} onNavigate={handleEntryClick} />
              ))}
            </div>
        }
      </div>
      <button
        type="button"
        onClick={goToReturns}
        className="flex items-center justify-center gap-1 w-full px-3 py-2 cursor-pointer transition-colors bg-transparent border-x-0 border-b-0 border-t border-border-default shrink-0"
        style={{ color: ACCENT }}
      >
        <span className="font-display text-[11px] font-semibold">
          {t('returnsSection.viewAll', { defaultValue: 'Tümünü gör' })}
        </span>
        <ChevronRight className="h-3 w-3" />
      </button>
    </Card>
  );
}

export default memo(ReturnsSectionImpl);
