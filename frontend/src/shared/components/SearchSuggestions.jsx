import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import { Search, X } from 'lucide-react';
import { TrendingUp, TrendingDown } from './AnimatedIcons';
import { unifiedMarketService } from '../services/unifiedMarketService';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../constants/assetTypes';
import { assetCodeLabel } from '../utils/assetCode';
import { formatPriceTRY, getChangeClass, changeColors } from '../utils/formatters';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities' };

function assetRoute(asset) {
  return (TYPE_ROUTES[asset.type] || '/market') + '/' + asset.code;
}

export default function SearchSuggestions({
  onSelect,
  placeholder = 'Hisse, kripto, döviz, fon ara...',
  navigateOnSelect = true,
  excludeCodes = [],
  filterType,
  variant = 'default',
}) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const inputRef = useRef(null);
  const containerRef = useRef(null);
  const timerRef = useRef(null);

  useEffect(() => {
    clearTimeout(timerRef.current);
    if (query.trim().length < 2) {
      setDebouncedQuery('');
      return;
    }
    timerRef.current = setTimeout(() => setDebouncedQuery(query.trim()), 250);
    return () => clearTimeout(timerRef.current);
  }, [query]);

  const { data, isFetching } = useQuery({
    queryKey: ['searchSuggestions', debouncedQuery, filterType],
    queryFn: () => unifiedMarketService.search({
      search: debouncedQuery,
      ...(filterType && { type: filterType }),
      size: 8,
    }),
    enabled: debouncedQuery.length >= 2,
    staleTime: 15_000,
  });

  const suggestions = (data?.content || []).filter(a => !excludeCodes.includes(a.code));

  useEffect(() => {
    setActiveIndex(-1);
  }, [debouncedQuery]);

  useEffect(() => {
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

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

  const handleKeyDown = (e) => {
    if (!open || suggestions.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex(i => (i + 1) % suggestions.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex(i => (i - 1 + suggestions.length) % suggestions.length);
    } else if (e.key === 'Enter' && activeIndex >= 0) {
      e.preventDefault();
      handleSelect(suggestions[activeIndex]);
    } else if (e.key === 'Escape') {
      setOpen(false);
      inputRef.current?.blur();
    }
  };

  const isHero = variant === 'hero';

  return (
    <div ref={containerRef} className="relative w-full">
      <div className="relative">
        <Search className={`absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none z-10 ${isHero ? 'h-5 w-5 text-accent' : 'h-3.5 w-3.5 text-fg-muted'}`} />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
          onFocus={() => { if (debouncedQuery.length >= 2) setOpen(true); }}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className={
            isHero
              ? 'w-full rounded-2xl border border-border-default bg-bg-elevated pl-12 pr-10 py-4 text-base text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 transition-all backdrop-blur-md'
              : 'w-full rounded-lg border border-border-default bg-bg-elevated pl-9 pr-8 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 transition-colors'
          }
        />
        {query && (
          <button
            onClick={() => { setQuery(''); setDebouncedQuery(''); setOpen(false); }}
            className={`absolute top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg transition-colors cursor-pointer bg-transparent border-none p-0 ${isHero ? 'right-4' : 'right-2.5'}`}
          >
            <X className={isHero ? 'h-5 w-5' : 'h-3.5 w-3.5'} />
          </button>
        )}
        {isFetching && debouncedQuery && (
          <span className={`absolute top-1/2 -translate-y-1/2 ${isHero ? 'right-12' : 'right-8'}`}>
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
            className={`absolute z-[100] w-full mt-1.5 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-xl shadow-xl overflow-hidden ${isHero ? 'max-h-[400px]' : 'max-h-[320px]'}`}
          >
            {suggestions.length === 0 && !isFetching ? (
              <div className="px-4 py-6 text-center text-sm text-fg-muted">
                &ldquo;{debouncedQuery}&rdquo; ile eşleşen sonuç bulunamadı
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
                      {asset.image ? (
                        <img src={asset.image} alt={asset.code} className="w-8 h-8 rounded-lg shrink-0" />
                      ) : (
                        <span
                          className="flex items-center justify-center w-8 h-8 rounded-lg text-[10px] font-bold shrink-0"
                          style={{ backgroundColor: typeColor + '18', color: typeColor }}
                        >
                          {assetCodeLabel(asset.type, asset.code).slice(0, 3).toUpperCase()}
                        </span>
                      )}

                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-semibold text-fg truncate">{assetCodeLabel(asset.type, asset.code)}</span>
                          <span
                            className="shrink-0 rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider"
                            style={{ backgroundColor: typeColor + '18', color: typeColor }}
                          >
                            {ASSET_TYPE_LABELS[asset.type] || asset.type}
                          </span>
                        </div>
                        {asset.name && (
                          <p className="text-xs text-fg-muted truncate">{asset.name}</p>
                        )}
                      </div>

                      <div className="text-right shrink-0">
                        <p className="text-sm font-mono font-semibold text-fg">{formatPriceTRY(asset.price)}</p>
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
