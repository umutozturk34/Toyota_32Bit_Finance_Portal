import { useState, useRef, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { Search, X } from 'lucide-react';
import { TrendingUp, TrendingDown } from '../feedback/AnimatedIcons';
import { ASSET_TYPE_COLORS } from '../../constants/assetTypes';
import { assetCodeLabel } from '../../utils/assetCode';
import { getChangeClass, changeColors } from '../../utils/formatters';
import { useMoney } from '../../hooks/useMoney';
import { priceCurrencyOf } from '../../utils/priceCurrency';
import useSearchSuggestions from '../../hooks/useSearchSuggestions';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };
const MACRO_TYPES = new Set(['MACRO_INFLATION', 'MACRO_RATE', 'MACRO_DEPOSIT']);

function assetRoute(asset) {
  const base = TYPE_ROUTES[asset.type] || '/market';
  return `${base}/${encodeURIComponent(asset.code)}`;
}

function friendlyName(asset) {
  if (!asset?.name) return null;
  return asset.name;
}

export default function SearchSuggestions({
  onSelect,
  placeholder,
  navigateOnSelect = true,
  excludeCodes = [],
  filterType,
  variant = 'default',
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

  const { containerRef, suggestions, activeIndex, setActiveIndex, isFetching, buildKeyDown } = useSearchSuggestions({
    query: debouncedQuery,
    filterType,
    excludeCodes,
    onClose: () => setOpen(false),
  });

  const handleSelect = useCallback((asset) => {
    setQuery('');
    setDebouncedQuery('');
    setOpen(false);
    if (onSelect) {
      onSelect(asset);
    } else if (navigateOnSelect) {
      navigate(assetRoute(asset));
    }
  }, [onSelect, navigateOnSelect, navigate]);

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
          value={query}
          onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
          onFocus={() => { if (debouncedQuery.length >= 2) setOpen(true); }}
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
            className={`absolute z-[100] w-full mt-1.5 rounded-xl border border-border-default shadow-xl overflow-hidden ${isHero ? 'max-h-[400px]' : 'max-h-[320px]'}`}
          >
            {suggestions.length === 0 && !isFetching ? (
              <div className="px-4 py-6 text-center text-sm text-fg-muted">
                {t('searchSuggestions.noMatch', { query: debouncedQuery })}
              </div>
            ) : (
              <div className="overflow-y-auto max-h-[inherit]">
                {suggestions.map((asset, i) => {
                  const cls = getChangeClass(asset.changePercent);
                  const isActive = i === activeIndex;
                  const typeColor = ASSET_TYPE_COLORS[asset.type] || '#8b5cf6';
                  return (
                    <button
                      key={`${asset.type}-${asset.code}`}
                      onClick={() => handleSelect(asset)}
                      onMouseEnter={() => setActiveIndex(i)}
                      className={`w-full flex items-center gap-3 px-4 py-3 text-left transition-colors cursor-pointer border-none ${
                        isActive ? 'bg-surface' : 'bg-transparent hover:bg-surface/50'
                      }`}
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
                            {friendlyName(asset) || assetCodeLabel(asset.type, asset.code)}
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
                        <p className="text-sm font-mono font-semibold text-fg">{money(asset.price, priceCurrencyOf(asset))}</p>
                        {asset.changePercent != null && (
                          <div className={`flex items-center justify-end gap-0.5 text-[11px] font-mono font-medium ${changeColors[cls]}`}>
                            {asset.changePercent > 0 ? <TrendingUp className="h-3 w-3" /> : asset.changePercent < 0 ? <TrendingDown className="h-3 w-3" /> : null}
                            {asset.changePercent > 0 ? '+' : ''}{asset.changePercent?.toFixed(2)}%
                          </div>
                        )}
                      </div>
                    </button>
                  );
                })}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
