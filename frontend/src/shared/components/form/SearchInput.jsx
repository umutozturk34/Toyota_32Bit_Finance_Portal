import { useState, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { Search, X } from 'lucide-react';
import { ASSET_TYPE_COLORS } from '../../constants/assetTypes';
import { getChangeClass, changeColors } from '../../utils/formatters';
import { useMoney } from '../../hooks/useMoney';
import { priceCurrencyOf } from '../../utils/priceCurrency';
import { assetCodeLabel } from '../../utils/assetCode';
import useSearchSuggestions from '../../hooks/useSearchSuggestions';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };

export default function SearchInput({ value, onChange, placeholder, debounceMs = 400, withSuggestions = false, filterType, suggestFn, suggestLabelFn }) {
  const { t } = useTranslation();
  const { format: money } = useMoney();
  const navigate = useNavigate();
  const placeholderText = placeholder ?? t('common.searchEllipsis');
  const [local, setLocal] = useState(value || '');
  const [sugQuery, setSugQuery] = useState('');
  const [open, setOpen] = useState(false);
  const timerRef = useRef(null);
  const sugTimerRef = useRef(null);

  const { containerRef, suggestions, activeIndex, setActiveIndex, buildKeyDown } = useSearchSuggestions({
    query: sugQuery,
    filterType,
    suggestFn,
    enabled: withSuggestions,
    onClose: () => setOpen(false),
  });

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

  const handleSelect = useCallback((asset) => {
    setOpen(false);
    clearTimeout(timerRef.current);
    if (suggestFn) {
      const rawLabel = asset.code || asset.seriesCode || asset.isinCode || '';
      const label = suggestLabelFn
        ? suggestLabelFn(asset)
        : assetCodeLabel(asset.type, rawLabel);
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

  const handleKeyDown = useCallback(
    (e) => buildKeyDown(handleSelect)(e),
    [buildKeyDown, handleSelect],
  );

  return (
    <div ref={containerRef} className="relative">
      <span className="absolute inset-y-0 left-3 z-10 flex items-center pointer-events-none">
        <Search className="h-3.5 w-3.5 text-fg-muted" />
      </span>
      <input
        type="text"
        value={local}
        onChange={handleChange}
        onFocus={() => { if (withSuggestions && sugQuery.length >= 2) setOpen(true); }}
        onKeyDown={handleKeyDown}
        placeholder={placeholderText}
        className="w-full rounded-lg border border-border-default bg-bg-elevated pl-9 pr-8 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 transition-colors"
      />
      {local && (
        <button
          onClick={handleClear}
          aria-label={t('common.clearSearch')}
          className="absolute right-2.5 inset-y-0 z-10 flex items-center text-fg-muted hover:text-fg transition-colors cursor-pointer bg-transparent border-none p-0"
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
              style={{ background: 'var(--color-bg-deep)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)' }}
              className="absolute z-50 w-72 mt-1.5 rounded-xl border border-border-default shadow-xl overflow-hidden"
            >
              <div className="overflow-y-auto max-h-[300px]">
                {suggestions.map((asset, i) => {
                  const rawCode = asset.code || asset.isinCode || asset.seriesCode || '';
                  const code = assetCodeLabel(asset.type, rawCode);
                  const name = asset.name || (asset.isinCode && asset.seriesCode ? asset.seriesCode : '');
                  const typeColor = ASSET_TYPE_COLORS[asset.type] || '#8b5cf6';
                  const typeLabel = asset.type
                    ? t(`assets.labels.${asset.type}`, { defaultValue: asset.type })
                    : (asset.bondType ? t(`market.bond.types.${asset.bondType}`, { defaultValue: asset.bondType }) : '');
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
                      {asset.image
                        ? (/^https?:\/\//i.test(asset.image)
                            ? <img src={asset.image} alt={code} className="w-7 h-7 rounded-lg shrink-0" />
                            : <span className="flex items-center justify-center w-7 h-7 rounded-lg text-lg shrink-0">{asset.image}</span>)
                        : (
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
                          <p className="text-xs font-mono font-semibold text-fg">{money(asset.price ?? asset.baseIndex, priceCurrencyOf(asset))}</p>
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
