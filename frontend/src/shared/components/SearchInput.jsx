import { useState, useRef, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'framer-motion';
import { Search, X } from 'lucide-react';
import { TrendingUp, TrendingDown } from './AnimatedIcons';
import { unifiedMarketService } from '../services/unifiedMarketService';
import { ASSET_TYPE_LABELS, ASSET_TYPE_COLORS } from '../constants/assetTypes';
import { assetCodeLabel } from '../constants/commodities';
import { formatPriceTRY, getChangeClass, changeColors } from '../utils/formatters';

import { BOND_TYPE_LABELS } from '../../features/bond/bondConstants';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds' };

export default function SearchInput({ value, onChange, placeholder = 'Ara...', debounceMs = 400, withSuggestions = false, filterType, suggestFn, suggestLabelFn }) {
  const navigate = useNavigate();
  const [local, setLocal] = useState(value || '');
  const [sugQuery, setSugQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const timerRef = useRef(null);
  const sugTimerRef = useRef(null);
  const containerRef = useRef(null);

  const handleChange = (e) => {
    const val = e.target.value;
    setLocal(val);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => onChange(val), debounceMs);

    if (withSuggestions) {
      clearTimeout(sugTimerRef.current);
      if (val.trim().length >= 2) {
        sugTimerRef.current = setTimeout(() => { setSugQuery(val.trim()); setOpen(true); }, 250);
      } else {
        setSugQuery('');
        setOpen(false);
      }
    }
  };

  const handleClear = () => {
    setLocal('');
    clearTimeout(timerRef.current);
    clearTimeout(sugTimerRef.current);
    onChange('');
    setSugQuery('');
    setOpen(false);
  };

  const { data } = useQuery({
    queryKey: ['searchSuggestions', sugQuery, filterType, !!suggestFn],
    queryFn: () => suggestFn
      ? suggestFn(sugQuery)
      : unifiedMarketService.search({
          search: sugQuery,
          ...(filterType && { type: filterType }),
          size: 6,
        }),
    enabled: withSuggestions && sugQuery.length >= 2,
    staleTime: 15_000,
  });

  const suggestions = suggestFn ? (data || []) : (data?.content || []);

  useEffect(() => { setActiveIndex(-1); }, [sugQuery]);

  useEffect(() => {
    if (!withSuggestions) return;
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [withSuggestions]);

  const handleSelect = useCallback((asset) => {
    setOpen(false);
    clearTimeout(timerRef.current);
    if (suggestFn) {
      const rawLabel = asset.code || asset.seriesCode || asset.isinCode || '';
      const label = suggestLabelFn
        ? suggestLabelFn(asset)
        : (asset.type ? assetCodeLabel(asset.type, rawLabel) : rawLabel);
      setLocal(label);
      setSugQuery('');
      onChange(label);
    } else {
      setLocal('');
      setSugQuery('');
      onChange('');
      const route = (TYPE_ROUTES[asset.type] || '/market') + '/' + asset.code;
      navigate(route);
    }
  }, [navigate, onChange, suggestFn, suggestLabelFn]);

  const handleKeyDown = (e) => {
    if (!withSuggestions || !open || suggestions.length === 0) return;
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
    }
  };

  return (
    <div ref={containerRef} className="relative">
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-muted pointer-events-none" />
      <input
        type="text"
        value={local}
        onChange={handleChange}
        onFocus={() => { if (withSuggestions && sugQuery.length >= 2) setOpen(true); }}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        className="w-full rounded-lg border border-border-default bg-bg-elevated pl-9 pr-8 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 transition-colors"
      />
      {local && (
        <button
          onClick={handleClear}
          className="absolute right-2.5 top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg transition-colors cursor-pointer bg-transparent border-none p-0"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      )}

      {withSuggestions && (
        <AnimatePresence>
          {open && sugQuery.length >= 2 && suggestions.length > 0 && (
            <motion.div
              initial={{ opacity: 0, y: -4, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -4, scale: 0.98 }}
              transition={{ duration: 0.15 }}
              className="absolute z-50 w-72 mt-1.5 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-xl shadow-xl overflow-hidden"
            >
              <div className="overflow-y-auto max-h-[300px]">
                {suggestions.map((asset, i) => {
                  const rawCode = asset.code || asset.isinCode || asset.seriesCode || '';
                  const code = asset.type ? assetCodeLabel(asset.type, rawCode) : rawCode;
                  const name = asset.name || (asset.isinCode && asset.seriesCode ? asset.seriesCode : '');
                  const typeColor = ASSET_TYPE_COLORS[asset.type] || '#8b5cf6';
                  const typeLabel = asset.type ? (ASSET_TYPE_LABELS[asset.type] || asset.type) : (BOND_TYPE_LABELS[asset.bondType] || asset.bondType || '');
                  const cls = getChangeClass(asset.changePercent);
                  return (
                    <button
                      key={`${asset.type || 'bond'}-${rawCode}-${i}`}
                      onClick={() => handleSelect(asset)}
                      onMouseEnter={() => setActiveIndex(i)}
                      className={`w-full flex items-center gap-2.5 px-3 py-2.5 text-left transition-colors cursor-pointer border-none ${
                        i === activeIndex ? 'bg-surface' : 'bg-transparent hover:bg-surface/50'
                      }`}
                    >
                      {asset.image ? (
                        <img src={asset.image} alt={code} className="w-7 h-7 rounded-lg shrink-0" />
                      ) : (
                        <span
                          className="flex items-center justify-center w-7 h-7 rounded-lg text-[9px] font-bold shrink-0"
                          style={{ backgroundColor: typeColor + '18', color: typeColor }}
                        >
                          {code.replace('.IS', '').slice(0, 3).toUpperCase()}
                        </span>
                      )}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="text-xs font-semibold text-fg truncate">{code}</span>
                          {typeLabel && (
                            <span
                              className="shrink-0 rounded px-1 py-px text-[8px] font-bold uppercase tracking-wider"
                              style={{ backgroundColor: typeColor + '18', color: typeColor }}
                            >
                              {typeLabel}
                            </span>
                          )}
                        </div>
                        {name && name !== code && <p className="text-[11px] text-fg-muted truncate">{name}</p>}
                      </div>
                      {(asset.price != null || asset.baseIndex != null) && (
                        <div className="text-right shrink-0">
                          <p className="text-xs font-mono font-semibold text-fg">{formatPriceTRY(asset.price ?? asset.baseIndex)}</p>
                          {asset.changePercent != null && (
                            <span className={`text-[10px] font-mono ${changeColors[cls]}`}>
                              {asset.changePercent > 0 ? '+' : ''}{asset.changePercent?.toFixed(2)}%
                            </span>
                          )}
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      )}
    </div>
  );
}
