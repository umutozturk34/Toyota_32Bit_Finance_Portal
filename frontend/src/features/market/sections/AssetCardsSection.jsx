import { memo, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import useNavigationStore from '../../../shared/stores/useNavigationStore';
import { AnimatePresence, motion } from 'framer-motion';
import { Layers, X } from 'lucide-react';
import { ArrowUpRight, ArrowDownRight } from '../../../shared/components/feedback/AnimatedIcons';
import { getChangeClass, changeColors, changeBg, formatPercentAbs, formatPriceTRY } from '../../../shared/utils/formatters';
import { useMoney } from '../../../shared/hooks/useMoney';
import { priceCurrencyOf } from '../../../shared/utils/priceCurrency';
import AssetCardChart from './AssetCardChart';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop', BOND: '/bonds' };
const TYPE_ABBR = { STOCK: 'STK', CRYPTO: 'CRY', FOREX: 'FX', FUND: 'FND', COMMODITY: 'CMD', BOND: 'BND', VIOP: 'VIOP' };

function shortLabel(asset) {
  const code = (asset.code || '').replace('.IS', '');
  // VIOP symbols carry an F_/O_ (futures/option) prefix that is just noise on the tiny card and pushes the
  // meaningful part (underlying + expiry) under the truncation — strip it so "F_XAUUSD0626" reads "XAUUSD0626".
  if (asset.type === 'VIOP') return code.replace(/^[FO]_/, '');
  return code;
}

// A bond's value is its nominal price in TRY — render it as a ₺ figure WITHOUT FX conversion (bonds always
// stay TRY, never shown in USD/EUR), bypassing the converting money() path so a USD/EUR display can't rebase it.
function bondPriceLabel(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '—';
  return formatPriceTRY(n);
}

function typeAbbr(type) {
  return TYPE_ABBR[type] || type;
}

function AssetCardImpl({ asset, index = 0, onClick, editMode, onRemove }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const handleClick = () => onClick?.(asset);
  const handleRemoveClick = () => onRemove?.(asset);
  const hasChange = asset.changePercent != null;
  const cls = getChangeClass(asset.changePercent);
  const isUp = (asset.changePercent ?? 0) > 0;
  const accent = !hasChange ? '#6366f1' : (isUp ? '#10b981' : '#ef4444');
  const isPending = asset._pending === true || asset.price == null;
  return (
    <div className="group/card relative h-full">
      <button
        type="button"
        onClick={editMode ? undefined : handleClick}
        disabled={editMode}
        className="group relative h-full w-full rounded-xl border border-border-default border-t-2 bg-bg-elevated cursor-pointer overflow-hidden text-left flex flex-col justify-between px-3 py-2.5 card-hover transition-all duration-200 hover:border-border-hover disabled:cursor-default"
        style={{ borderTopColor: accent }}
      >
        {!isPending && (
          <AssetCardChart
            assetType={asset.type}
            assetCode={asset.code}
            changePercent={asset.changePercent ?? 0}
            color={accent}
            delayMs={index * 70}
          />
        )}
        <div className="relative z-10 space-y-1">
          <div className="flex items-center justify-between gap-1.5">
            <div className="flex items-center gap-1.5 min-w-0">
              {asset.image
                ? (/^https?:\/\//i.test(asset.image)
                    ? <img src={asset.image} alt="" loading="lazy" decoding="async" width="16" height="16" className="w-4 h-4 rounded-full ring-1 ring-border-default shrink-0" />
                    : <span className="w-4 h-4 rounded-full shrink-0 flex items-center justify-center text-xs leading-none">{asset.image}</span>)
                : <span className="w-4 h-4 rounded-full shrink-0 flex items-center justify-center text-[7px] font-bold text-white" style={{ background: accent }}>{shortLabel(asset).slice(0, 2)}</span>}
              <span className="font-display text-[12px] font-bold text-fg leading-none truncate">{shortLabel(asset)}</span>
            </div>
            <span className="font-mono text-[8px] font-semibold tracking-wider uppercase leading-none px-1 py-0.5 rounded-[3px] bg-surface text-fg-muted shrink-0">
              {typeAbbr(asset.type)}
            </span>
          </div>
          {hasChange && (
            <div>
              <span className={`inline-flex items-center gap-0.5 rounded-md px-1 py-0.5 text-[9px] sm:text-[10px] font-mono font-semibold tabular-nums ${changeBg[cls]} ${changeColors[cls]}`}>
                {isUp ? <ArrowUpRight className="h-2.5 w-2.5" /> : <ArrowDownRight className="h-2.5 w-2.5" />}
                {formatPercentAbs(asset.changePercent)}
              </span>
            </div>
          )}
        </div>
        <div className="relative z-10 min-w-0">
          <p className="font-mono text-[12px] min-[480px]:text-[13px] sm:text-[15px] font-bold tracking-tight tabular-nums leading-none truncate">
            {isPending
              ? <span className="text-fg-subtle inline-flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-accent animate-pulse" />
                  <span className="text-fg-muted">— ₺</span>
                </span>
              : <span className="text-fg">{asset.type === 'BOND' ? bondPriceLabel(asset.price) : money(asset.price, priceCurrencyOf(asset))}</span>}
          </p>
        </div>
      </button>
      {editMode && onRemove && (
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); handleRemoveClick(); }}
          onPointerDown={(e) => e.stopPropagation()}
          className="widget-no-drag absolute -bottom-2 left-1/2 -translate-x-1/2 z-50 flex items-center justify-center w-6 h-6 rounded-full border border-danger/70 bg-bg-deep text-danger hover:bg-danger/20 hover:border-danger transition-all duration-150 cursor-pointer shadow-lg shadow-black/30"
          style={{ pointerEvents: 'auto' }}
          title={t('assetCards.remove', { code: shortLabel(asset) })}
          aria-label={t('assetCards.remove', { code: shortLabel(asset) })}
        >
          <X className="h-2.5 w-2.5" />
        </button>
      )}
    </div>
  );
}

const AssetCard = memo(AssetCardImpl);

export default function AssetCardsSection({ data, editMode = false, config = {}, onConfigChange }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const setOrigin = useNavigationStore((s) => s.setOrigin);
  const items = useMemo(() => data?.items ?? [], [data]);

  const visibleItems = useMemo(() => {
    // Asset cards are for tradeable price instruments; macro indicators (CPI/rates/deposits) have their own
    // section and don't fit the ₺-price card — drop any that linger in a saved config so no broken macro card
    // renders. (New ones are already blocked from the config search.)
    const isCardable = (it) => !String(it?.type || '').startsWith('MACRO_');
    if (!Array.isArray(config?.assetCodes)) return items.filter(isCardable);
    return config.assetCodes
      .map((c) => {
        const found = items.find((it) => it.type === c.type && it.code === c.code);
        return found || { ...c, _pending: true };
      })
      .filter(isCardable);
  }, [items, config?.assetCodes]);

  const goToAsset = useCallback((asset) => {
    setOrigin('/market', window.scrollY);
    navigate(`${TYPE_ROUTES[asset.type] ?? '/market'}/${asset.code}`, { state: { from: '/market' } });
  }, [navigate, setOrigin]);

  const handleRemove = useCallback((asset) => {
    const explicit = Array.isArray(config?.assetCodes) && config.assetCodes.length > 0;
    const baseCodes = explicit
      ? config.assetCodes
      : items.map((it) => ({ type: it.type, code: it.code }));
    const next = baseCodes.filter((c) => !(c.type === asset.type && c.code === asset.code));
    onConfigChange?.({ ...config, assetCodes: next });
  }, [config, items, onConfigChange]);

  if (visibleItems.length === 0) {
    if (!editMode) return null;
    return (
      <div className="h-full rounded-2xl border border-dashed border-border-default bg-bg-elevated/40 flex flex-col items-center justify-center px-4 py-6 text-center">
        <Layers className="h-5 w-5 text-fg-subtle mb-2" />
        <p className="font-mono text-[10px] tracking-[0.18em] uppercase text-fg-muted">{t('assetCards.emptyPanel')}</p>
        <p className="text-[10px] text-fg-subtle mt-1" dangerouslySetInnerHTML={{ __html: t('assetCards.emptyHint') }} />
      </div>
    );
  }

  return (
    <div
      className="h-full grid gap-2 grid-cols-2 sm:grid-cols-[repeat(auto-fit,minmax(110px,1fr))]"
      style={{ gridAutoRows: '1fr' }}
    >
      <AnimatePresence initial={false}>
        {visibleItems.map((asset, i) => {
          const key = `${asset.type}-${asset.code}`;
          return (
            <motion.div
              key={key}
              initial={{ opacity: 0, scale: 0.96 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.92 }}
              transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
              className="h-full"
            >
              <AssetCard
                asset={asset}
                index={i}
                onClick={goToAsset}
                editMode={editMode}
                onRemove={editMode ? handleRemove : undefined}
              />
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
