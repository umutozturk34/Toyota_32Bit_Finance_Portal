import { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { Search, X, Clock } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../feedback/AnimatedIcons';
import { ASSET_TYPE_COLORS } from '../../constants/assetTypes';
import { assetCodeLabel } from '../../utils/assetCode';
import { commodityLabel } from '../../utils/commodityName';
import { getChangeClass, changeColors } from '../../utils/formatters';
import { useMoney } from '../../hooks/useMoney';
import { priceCurrencyOf } from '../../utils/priceCurrency';
import useSearchSuggestions from '../../hooks/useSearchSuggestions';
import { useRecentSearches, useRecordRecentSearch, useClearRecentSearches, useRemoveRecentSearch } from '../../hooks/useRecentSearches';
import IndicatorHistoryModal from '../../../features/macro/components/IndicatorHistoryModal';
import { useMacroIndicators } from '../../../features/macro/hooks/useMacroIndicators';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };
const MACRO_TYPES = new Set(['MACRO_INFLATION', 'MACRO_RATE', 'MACRO_DEPOSIT']);

function assetRoute(asset) {
  if (asset.type === 'BOND') {
    return `/bonds/${encodeURIComponent(asset.code)}`;
  }
  const base = TYPE_ROUTES[asset.type];
  if (!base) return '/market';
  return `${base}/${encodeURIComponent(asset.code)}`;
}

function friendlyName(asset) {
  if (!asset?.name) return null;
  return asset.name;
}

// Macro indicators are rates / index levels, NOT ₺ prices: a deposit or policy rate renders as a
// percentage and a CPI-style index as a plain number — never with a currency symbol (the "₺0,31"
// bug on a deposit rate). Unit drives it; when unit is absent, only inflation is treated as an index.
function formatMacroValue(value, unit, type) {
  const v = Number(value);
  if (!Number.isFinite(v)) return '—';
  const isPercent = unit ? unit === 'PERCENT' : type !== 'MACRO_INFLATION';
  return isPercent ? `%${v.toFixed(2)}` : v.toLocaleString('tr-TR', { maximumFractionDigits: 2 });
}

export default function SearchSuggestions({
  onSelect,
  placeholder,
  navigateOnSelect = true,
  excludeCodes = [],
  excludeTypes = [],
  filterType,
  variant = 'default',
  secondaryAction,
  autoFocus = false,
  onAfterSelect,
}) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const navigate = useNavigate();
  const placeholderText = placeholder ?? t('searchSuggestions.placeholder');
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [open, setOpen] = useState(false);
  const inputRef = useRef(null);
  const timerRef = useRef(null);

  const [macroPreviewCode, setMacroPreviewCode] = useState(null);
  const { data: macroIndicators = [] } = useMacroIndicators();
  const macroPreview = macroPreviewCode
    ? macroIndicators.find((i) => i.code === macroPreviewCode) ?? null
    : null;
  const macroUnitByCode = useMemo(
    () => Object.fromEntries((macroIndicators || []).map((m) => [m.code, m.unit])),
    [macroIndicators],
  );

  const trimmedQuery = query.trim();
  const tooShort = trimmedQuery.length < 2;
  const [trackedQuery, setTrackedQuery] = useState(query);
  if (query !== trackedQuery) {
    setTrackedQuery(query);
    if (tooShort && debouncedQuery !== '') {
      setDebouncedQuery('');
    }
  }

  useEffect(() => {
    clearTimeout(timerRef.current);
    if (tooShort) return undefined;
    timerRef.current = setTimeout(() => setDebouncedQuery(trimmedQuery), 250);
    return () => clearTimeout(timerRef.current);
  }, [trimmedQuery, tooShort]);

  // Command-palette usage: focus the input on mount so the user can type immediately without a second
  // click. Programmatic focus fires the input's onFocus, which opens the recent-searches panel — so we
  // deliberately do NOT setOpen here (that would be a setState-in-effect). Hero/page usage leaves it off.
  useEffect(() => {
    if (!autoFocus) return;
    inputRef.current?.focus();
  }, [autoFocus]);

  const { containerRef, suggestions, activeIndex, setActiveIndex, isFetching, buildKeyDown } = useSearchSuggestions({
    query: debouncedQuery,
    filterType,
    excludeCodes,
    excludeTypes,
    pageSize: 12,
    onClose: () => setOpen(false),
  });

  const { data: recentSearches = [] } = useRecentSearches();
  const { mutate: recordRecent } = useRecordRecentSearch();
  const { mutate: clearRecent } = useClearRecentSearches();
  const { mutate: removeRecent } = useRemoveRecentSearch();

  // Persist the picked asset (tagged with its type) so it can resurface as a recent search. Macro
  // indicators open a preview rather than an asset page, so they are intentionally not recorded.
  const trackRecent = useCallback((asset) => {
    if (!asset?.type || MACRO_TYPES.has(asset.type) || asset.type.startsWith('MACRO')) return;
    if (!asset.code) return;
    recordRecent({ code: asset.code, type: asset.type, name: asset.name });
  }, [recordRecent]);

  // filterType may be a single type ('STOCK') or a comma-joined set ('STOCK,CRYPTO,...') as passed by
  // Compare's "assets" mode; match membership so the recent list isn't silently empty in that case.
  const allowedTypes = filterType ? new Set(filterType.split(',')) : null;
  const recentItems = allowedTypes
    ? recentSearches.filter((item) => allowedTypes.has(item.type))
    : recentSearches;

  const handleSelect = useCallback((asset) => {
    trackRecent(asset);
    setQuery('');
    setDebouncedQuery('');
    setOpen(false);
    if (onSelect) {
      onSelect(asset);
      return;
    }
    if (!navigateOnSelect) return;
    if (MACRO_TYPES.has(asset.type)) {
      // Macro picks open an in-place preview (rendered below); keep any hosting palette open for it.
      setMacroPreviewCode(asset.code);
      return;
    }
    navigate(assetRoute(asset));
    onAfterSelect?.();
  }, [onSelect, navigateOnSelect, navigate, trackRecent, onAfterSelect]);

  const handleRecentSelect = useCallback((item) => {
    setQuery('');
    setDebouncedQuery('');
    setOpen(false);
    if (onSelect) {
      onSelect(item);
      return;
    }
    navigate(assetRoute(item));
    onAfterSelect?.();
  }, [onSelect, navigate, onAfterSelect]);

  const handleEscape = useCallback(() => {
    setOpen(false);
    inputRef.current?.blur();
  }, []);

  const handleKeyDown = useCallback(
    (e) => buildKeyDown(handleSelect, handleEscape)(e),
    [buildKeyDown, handleSelect, handleEscape],
  );

  const isHero = variant === 'hero';

  return (
    <div ref={containerRef} className="relative w-full">
      <div className="relative">
        <span className="absolute inset-y-0 left-3.5 z-10 flex items-center pointer-events-none">
          <Search className={`${isHero ? 'h-5 w-5 text-accent' : 'h-3.5 w-3.5 text-fg-muted'}`} />
        </span>
        <input
          ref={inputRef}
          type="text"
          maxLength={64}
          value={query}
          onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={placeholderText}
          className={
            isHero
              ? 'w-full rounded-2xl border border-border-default bg-bg-elevated pl-12 pr-10 py-4 text-base text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 transition-all backdrop-blur-md'
              : 'w-full rounded-lg border border-border-default bg-bg-elevated pl-9 pr-8 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 transition-colors'
          }
        />
        {query && (
          <button
            onClick={() => { setQuery(''); setDebouncedQuery(''); setOpen(false); }}
            aria-label={t('common.clearSearch')}
            className={`absolute inset-y-0 z-10 flex items-center text-fg-muted hover:text-fg transition-colors cursor-pointer bg-transparent border-none p-0 ${isHero ? 'right-4' : 'right-2.5'}`}
          >
            <X className={isHero ? 'h-5 w-5' : 'h-3.5 w-3.5'} />
          </button>
        )}
        {isFetching && debouncedQuery && (
          <span className={`absolute inset-y-0 z-10 flex items-center ${isHero ? 'right-12' : 'right-8'}`}>
            <span className="block h-3.5 w-3.5 rounded-full border-2 border-accent border-t-transparent animate-spin" />
          </span>
        )}
      </div>

      <AnimatePresence>
        {open && trimmedQuery.length < 2 && recentItems.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.98 }}
            transition={{ duration: 0.15 }}
            style={{
              background: 'var(--color-bg-deep)',
              backdropFilter: 'blur(20px)',
              WebkitBackdropFilter: 'blur(20px)',
            }}
            className={`absolute z-[100] w-full mt-1.5 rounded-xl border border-border-default shadow-xl overflow-hidden flex flex-col ${isHero ? 'max-h-[400px]' : 'max-h-[320px]'}`}
          >
            <div className="flex items-center justify-between px-4 py-2 border-b border-border-default shrink-0">
              <span className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider text-fg-muted">
                <Clock className="h-3 w-3" />
                {t('searchSuggestions.recentSearches')}
              </span>
              <button
                onClick={() => clearRecent()}
                className="text-[11px] font-medium text-fg-muted hover:text-fg transition-colors cursor-pointer bg-transparent border-none p-0"
              >
                {t('searchSuggestions.clearRecent')}
              </button>
            </div>
            <div className="flex-auto min-h-0 overflow-y-auto">
              {recentItems.map((item) => {
                const typeColor = ASSET_TYPE_COLORS[item.type] || '#8b5cf6';
                return (
                  <div
                    key={`recent-${item.type}-${item.code}`}
                    className="group w-full flex items-center hover:bg-surface/50 transition-colors"
                  >
                    <button
                      onClick={() => handleRecentSelect(item)}
                      className="flex-1 min-w-0 flex items-center gap-3 px-4 py-2.5 text-left bg-transparent border-none cursor-pointer"
                    >
                      <span
                        className="flex items-center justify-center w-8 h-8 rounded-lg text-[10px] font-bold shrink-0"
                        style={{ backgroundColor: typeColor + '18', color: typeColor }}
                      >
                        {assetCodeLabel(item.type, item.code).slice(0, 3).toUpperCase()}
                      </span>
                      <div className="flex-1 min-w-0">
                        <span className="block text-sm font-semibold text-fg truncate">
                          {commodityLabel(t, item.type, item.code, item.name || assetCodeLabel(item.type, item.code))}
                        </span>
                        <span className="block text-[11px] text-fg-subtle font-mono truncate">
                          {assetCodeLabel(item.type, item.code)}
                        </span>
                      </div>
                      <span
                        className="shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider"
                        style={{ backgroundColor: typeColor + '18', color: typeColor }}
                      >
                        {t(`assets.labels.${item.type}`, { defaultValue: item.type })}
                      </span>
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); removeRecent({ code: item.code, type: item.type }); }}
                      aria-label={t('searchSuggestions.removeRecent', { defaultValue: 'Kaldır' })}
                      className="shrink-0 mr-2 flex items-center justify-center h-7 w-7 rounded-md text-fg-subtle hover:text-rose-400 hover:bg-rose-500/10 transition-colors cursor-pointer border-none bg-transparent opacity-0 group-hover:opacity-100"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </div>
                );
              })}
            </div>
          </motion.div>
        )}
        {open && debouncedQuery.length >= 2 && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.98 }}
            transition={{ duration: 0.15 }}
            style={{
              background: 'var(--color-bg-deep)',
              backdropFilter: 'blur(20px)',
              WebkitBackdropFilter: 'blur(20px)',
            }}
            className={`absolute z-[100] w-full mt-1.5 rounded-xl border border-border-default shadow-xl overflow-hidden flex flex-col ${isHero ? 'max-h-[400px]' : 'max-h-[320px]'}`}
          >
            {suggestions.length === 0 && !isFetching ? (
              <div className="px-4 py-6 text-center text-sm text-fg-muted [overflow-wrap:anywhere]">
                {t('searchSuggestions.noMatch', { query: debouncedQuery })}
              </div>
            ) : (
              <div className="flex-auto min-h-0 overflow-y-auto">
                {suggestions.map((asset, i) => {
                  const cls = getChangeClass(asset.changePercent);
                  const isActive = i === activeIndex;
                  const typeColor = ASSET_TYPE_COLORS[asset.type] || '#8b5cf6';
                  const showSecondary = secondaryAction
                    && (!secondaryAction.shouldShow || secondaryAction.shouldShow(asset));
                  const SecondaryIcon = secondaryAction?.icon;
                  return (
                    <div
                      key={`${asset.type}-${asset.code}`}
                      onMouseEnter={() => setActiveIndex(i)}
                      className={`w-full flex items-center transition-colors ${
                        isActive ? 'bg-surface' : 'bg-transparent hover:bg-surface/50'
                      }`}
                    >
                      <button
                        onClick={() => handleSelect(asset)}
                        className="flex-1 min-w-0 flex items-center gap-3 px-4 py-3 text-left bg-transparent border-none cursor-pointer"
                      >
                        {asset.image
                          ? (/^https?:\/\//i.test(asset.image)
                              ? <img src={asset.image} alt={asset.code} className="w-8 h-8 rounded-lg shrink-0" />
                              : <span className="flex items-center justify-center w-8 h-8 rounded-lg text-xl shrink-0">{asset.image}</span>)
                          : (
                            <span
                              className="flex items-center justify-center w-8 h-8 rounded-lg text-[10px] font-bold shrink-0"
                              style={{ backgroundColor: typeColor + '18', color: typeColor }}
                            >
                              {assetCodeLabel(asset.type, asset.code).slice(0, 3).toUpperCase()}
                            </span>
                          )}

                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-semibold text-fg truncate">
                              {commodityLabel(t, asset.type, asset.code, friendlyName(asset) || assetCodeLabel(asset.type, asset.code))}
                            </span>
                            <span
                              className="shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider"
                              style={{ backgroundColor: typeColor + '18', color: typeColor }}
                            >
                              {t(`assets.labels.${asset.type}`, { defaultValue: asset.type })}
                            </span>
                          </div>
                          {asset.name && asset.code !== asset.name && !MACRO_TYPES.has(asset.type) && (
                            <p className="text-[11px] text-fg-subtle font-mono truncate">{assetCodeLabel(asset.type, asset.code)}</p>
                          )}
                        </div>

                        <div className="text-right shrink-0">
                          <p className="text-sm font-mono font-semibold text-fg">
                            {MACRO_TYPES.has(asset.type)
                              ? formatMacroValue(asset.price, macroUnitByCode[asset.code], asset.type)
                              : money(asset.price, priceCurrencyOf(asset))}
                          </p>
                          {asset.changePercent != null && (
                            <div className={`flex items-center justify-end gap-0.5 text-[11px] font-mono font-medium ${changeColors[cls]}`}>
                              {asset.changePercent > 0 ? <TrendingUp className="h-3 w-3" /> : asset.changePercent < 0 ? <TrendingDown className="h-3 w-3" /> : null}
                              {asset.changePercent > 0 ? '+' : ''}{asset.changePercent?.toFixed(2)}%
                            </div>
                          )}
                        </div>
                      </button>
                      {showSecondary && (
                        <button
                          onClick={(e) => { e.stopPropagation(); secondaryAction.onClick(asset); setQuery(''); setDebouncedQuery(''); setOpen(false); }}
                          title={secondaryAction.label}
                          aria-label={secondaryAction.label}
                          className="shrink-0 mr-2 flex items-center justify-center h-8 w-8 rounded-lg bg-accent/10 text-accent hover:bg-accent/20 transition-colors cursor-pointer border-none"
                        >
                          {SecondaryIcon && <SecondaryIcon className="h-4 w-4" />}
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      <IndicatorHistoryModal indicator={macroPreview} onClose={() => setMacroPreviewCode(null)} />
    </div>
  );
}
