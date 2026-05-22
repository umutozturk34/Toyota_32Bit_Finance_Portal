import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Sparkles, Search } from 'lucide-react';
import SearchSuggestions from '../../../shared/components/form/SearchSuggestions';
import { INSTRUMENT_TYPES, PRESET_INSTRUMENTS, SERIES_COLORS } from '../constants';

const MARKET_TO_ANALYTICS = {
  STOCK: 'SPOT',
  CRYPTO: 'CRYPTO',
  FOREX: 'FOREX',
  FUND: 'FUND',
  COMMODITY: 'COMMODITY',
  VIOP: 'VIOP',
  BOND: 'BOND',
  MACRO_DEPOSIT: 'DEPOSIT',
  MACRO_INFLATION: 'MACRO',
  MACRO_RATE: 'MACRO',
};

export default function InstrumentPicker({ value, onChange, max = 6 }) {
  const { t } = useTranslation();
  const [searchOpen, setSearchOpen] = useState(false);

  const excludedCodes = useMemo(() => value.map((v) => v.code), [value]);

  function add(instrument) {
    if (value.length >= max) return;
    if (value.some((v) => v.type === instrument.type && v.code === instrument.code)) return;
    onChange([...value, instrument]);
  }

  function remove(idx) {
    onChange(value.filter((_, i) => i !== idx));
  }

  function handleSearchSelect(asset) {
    const analyticsType = MARKET_TO_ANALYTICS[asset.type];
    if (!analyticsType) return;
    add({ type: analyticsType, code: asset.code, name: asset.name || asset.code });
    setSearchOpen(false);
  }

  const reachedMax = value.length >= max;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h4 className="text-xs font-mono uppercase tracking-[0.18em] text-fg-muted">
          {t('analytics.instruments', { defaultValue: 'Enstrümanlar' })}
          <span className="ml-2 text-fg-subtle tabular-nums">{value.length}/{max}</span>
        </h4>
        <button
          type="button"
          onClick={() => setSearchOpen((s) => !s)}
          disabled={reachedMax}
          className="inline-flex items-center gap-1.5 text-xs font-semibold rounded-lg px-3 py-1.5 bg-accent/10 text-accent hover:bg-accent/20 transition-colors border-none disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
        >
          <Search className="h-3.5 w-3.5" />
          {t('analytics.searchInstrument', { defaultValue: 'Ara' })}
        </button>
      </div>

      <div className="flex flex-wrap gap-2 min-h-[40px]">
        <AnimatePresence>
          {value.map((inst, idx) => {
            const typeDef = INSTRUMENT_TYPES.find((it) => it.id === inst.type);
            const color = SERIES_COLORS[idx % SERIES_COLORS.length];
            return (
              <motion.span
                key={`${inst.type}|${inst.code}`}
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.9 }}
                className="group/chip inline-flex items-center gap-2 rounded-xl pl-2 pr-1.5 py-1.5 text-xs font-semibold"
                style={{ background: `${color}1a`, boxShadow: `inset 0 0 0 1px ${color}40` }}
              >
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: color }} />
                <span className="font-mono text-[10px] uppercase tracking-[0.12em] text-fg-subtle">
                  {typeDef?.labelKey ? t(`analytics.${typeDef.labelKey}`, { defaultValue: inst.type }) : inst.type}
                </span>
                <span className="text-fg">{inst.name || inst.code}</span>
                <button
                  type="button"
                  onClick={() => remove(idx)}
                  className="ml-1 h-5 w-5 rounded-md flex items-center justify-center text-fg-muted hover:text-fg hover:bg-bg-elevated cursor-pointer border-none"
                  aria-label="remove"
                >
                  <X className="h-3 w-3" />
                </button>
              </motion.span>
            );
          })}
        </AnimatePresence>
        {value.length === 0 && (
          <div className="text-xs text-fg-subtle font-mono italic px-1 py-2">
            {t('analytics.noInstruments', { defaultValue: 'Hiç enstrüman seçilmedi. Aşağıdan ekle veya hızlı önerilerden seç.' })}
          </div>
        )}
      </div>

      <AnimatePresence>
        {searchOpen && !reachedMax && (
          <motion.div
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            className="relative z-50 rounded-xl border border-border-default/60 bg-bg-elevated p-3 space-y-2"
          >
            <div className="text-[10px] font-mono uppercase tracking-[0.18em] text-fg-muted">
              {t('analytics.searchHint', { defaultValue: 'Hisse, fon, döviz, kripto, emtia, bono, makro ara' })}
            </div>
            <SearchSuggestions
              onSelect={handleSearchSelect}
              navigateOnSelect={false}
              excludeCodes={excludedCodes}
              placeholder={t('analytics.searchPlaceholder', { defaultValue: 'ASELS, altın, bitcoin, TP.TRYTAS, TRT...' })}
            />
          </motion.div>
        )}
      </AnimatePresence>

      <div className="relative z-0 rounded-xl border border-border-default/60 bg-bg-elevated/30 p-3 space-y-2.5">
        <div className="flex items-center gap-2 text-[10px] font-mono uppercase tracking-[0.18em] text-fg-muted">
          <Sparkles className="h-3 w-3" />
          {t('analytics.quickPick', { defaultValue: 'Hızlı seç' })}
        </div>
        <div className="flex flex-wrap gap-1.5">
          {PRESET_INSTRUMENTS.map((p) => {
            const taken = value.some((v) => v.type === p.type && v.code === p.code);
            return (
              <button
                key={`${p.type}|${p.code}`}
                type="button"
                onClick={() => add(p)}
                disabled={taken || reachedMax}
                className={`text-[11px] font-medium rounded-lg px-2.5 py-1 cursor-pointer transition-colors border-none ${
                  taken
                    ? 'opacity-30 cursor-not-allowed bg-bg-base/40 text-fg-subtle'
                    : reachedMax
                      ? 'opacity-30 cursor-not-allowed bg-bg-base/40 text-fg-subtle'
                      : 'bg-bg-base/60 hover:bg-accent/10 hover:text-accent text-fg'
                }`}
              >
                {p.name}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
