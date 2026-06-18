import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Tag, ChevronDown, Check } from 'lucide-react';

export default function DepositTypeSelect({ ratesLoading, rateMenuOpen, setRateMenuOpen, selectedRate, localeTag, depositRates, indicatorCode, onRateSelect, customRateKey }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-1.5 sm:col-span-2">
      <label className="text-xs font-medium text-fg-muted flex items-center justify-between gap-1.5">
        <span className="inline-flex items-center gap-1.5">
          <Tag className="h-3 w-3" />
          {t('deposits.fields.depositType')}
        </span>
        {ratesLoading && <span className="text-[10px] text-fg-subtle">{t('common.loading')}</span>}
      </label>
      <div className="relative">
        <button
          type="button"
          onClick={() => setRateMenuOpen((o) => !o)}
          aria-haspopup="listbox"
          aria-expanded={rateMenuOpen}
          className={`w-full flex items-center justify-between gap-2 rounded-lg border bg-bg-base px-3 py-2.5 text-sm outline-none transition-all cursor-pointer ${
            rateMenuOpen ? 'border-accent/50 ring-1 ring-accent/30' : 'border-border-default hover:border-accent/30'
          }`}
        >
          <span className="inline-flex items-center gap-2 min-w-0 truncate">
            {selectedRate ? (
              <>
                <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md bg-accent/15 text-[10px] font-bold text-accent">
                  {t(`marketOverview.macro.maturity${selectedRate.maturity}`).replace(/\s/g, '').slice(0, 3)}
                </span>
                <span className="text-fg truncate">{t(`marketOverview.macro.maturity${selectedRate.maturity}`)}</span>
                <span className="font-mono text-accent">%{selectedRate.lastValue.toLocaleString(localeTag)}</span>
              </>
            ) : (
              <span className="text-fg-muted">{t('deposits.fields.depositTypeCustom')}</span>
            )}
          </span>
          <ChevronDown className={`h-4 w-4 shrink-0 transition-transform ${rateMenuOpen ? 'rotate-180 text-accent' : 'text-fg-subtle'}`} />
        </button>
        <AnimatePresence>
          {rateMenuOpen && (
            <>
              <div className="fixed inset-0 z-[1]" onClick={() => setRateMenuOpen(false)} aria-hidden />
              <motion.div
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.14 }}
                role="listbox"
                style={{ background: 'var(--color-bg-deep)', backdropFilter: 'blur(20px)', WebkitBackdropFilter: 'blur(20px)' }}
                className="absolute z-20 mt-1.5 w-full overflow-hidden rounded-xl border border-border-default p-1 shadow-2xl shadow-black/40 ring-1 ring-black/5"
              >
                {depositRates.map((r) => {
                  const active = r.code === indicatorCode;
                  return (
                    <button
                      key={r.code}
                      type="button"
                      onClick={() => onRateSelect(r.code)}
                      className={`w-full flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-sm transition-colors border-none cursor-pointer ${
                        active ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg hover:bg-surface'
                      }`}
                    >
                      <span className="inline-flex items-center gap-2 min-w-0 truncate">
                        {active ? <Check className="h-3.5 w-3.5 shrink-0" /> : <span className="w-3.5 shrink-0" />}
                        {t(`marketOverview.macro.maturity${r.maturity}`)}
                      </span>
                      <span className={`font-mono text-xs ${active ? 'text-accent' : 'text-fg-muted'}`}>
                        %{r.lastValue.toLocaleString(localeTag)}
                      </span>
                    </button>
                  );
                })}
                <button
                  type="button"
                  onClick={() => onRateSelect(customRateKey)}
                  className={`w-full flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors border-none cursor-pointer ${
                    !selectedRate ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg hover:bg-surface'
                  }`}
                >
                  {!selectedRate ? <Check className="h-3.5 w-3.5 shrink-0" /> : <span className="w-3.5 shrink-0" />}
                  {t('deposits.fields.depositTypeCustom')}
                </button>
              </motion.div>
            </>
          )}
        </AnimatePresence>
      </div>
      {!ratesLoading && depositRates.length === 0 && (
        <p className="text-[10px] text-fg-subtle">{t('deposits.fields.depositTypeNoRates')}</p>
      )}
    </div>
  );
}
