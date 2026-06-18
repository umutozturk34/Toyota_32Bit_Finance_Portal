import { motion, AnimatePresence } from 'framer-motion';
import { Briefcase, ChevronDown } from 'lucide-react';
import SearchSuggestions from '../../../../shared/components/form/SearchSuggestions';
import { MAX_COMPARE } from '../../lib/compareConstants';

export default function ComparePickerRow({
  selected,
  addAsset,
  modeDef,
  mode,
  userPortfolios,
  portfolioPickerRef,
  portfolioPickerOpen,
  setPortfolioPickerOpen,
  addPortfolio,
  t,
}) {
  if (selected.length >= MAX_COMPARE) return null;
  return (
    <div className="flex flex-col sm:flex-row items-stretch gap-2">
      <div className="flex-1 min-w-0">
        <SearchSuggestions
          onSelect={addAsset}
          navigateOnSelect={false}
          excludeCodes={selected.map((s) => s.code)}
          excludeTypes={['BOND']}
          filterType={modeDef.filterType}
          placeholder={mode === 'assets'
            ? t('analytics.compareSearchAssets', { defaultValue: 'Hisse, kripto, fon, döviz, emtia ara…' })
            : t('analytics.compareSearchMixed', { defaultValue: 'Asset veya makro indikatör ara…' })}
        />
      </div>
      {(userPortfolios?.length ?? 0) > 0 && (
        <div ref={portfolioPickerRef} className="relative">
          <button
            type="button"
            onClick={() => setPortfolioPickerOpen((v) => !v)}
            className="w-full sm:w-auto h-full inline-flex items-center justify-center gap-1.5 rounded-md border border-border-default bg-bg-elevated hover:bg-accent/8 hover:border-accent/40 px-3 py-2 text-xs font-mono font-semibold text-fg-muted hover:text-accent transition-colors cursor-pointer"
            title={t('analytics.comparePortfolioCta', { defaultValue: 'Portföyünü ekle' })}
          >
            <Briefcase className="h-3.5 w-3.5" />
            {t('analytics.comparePortfolioCta', { defaultValue: 'Portföyünü ekle' })}
            <ChevronDown className={`h-3 w-3 transition-transform ${portfolioPickerOpen ? 'rotate-180' : ''}`} />
          </button>
          <AnimatePresence>
            {portfolioPickerOpen && (
              <motion.div
                initial={{ opacity: 0, y: -4, scale: 0.98 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -4, scale: 0.98 }}
                transition={{ duration: 0.14 }}
                className="absolute z-30 right-0 mt-1 w-56 max-w-[calc(100vw-2rem)] rounded-lg border border-border-default bg-bg-elevated shadow-lg overflow-hidden"
              >
                <div className="px-3 py-1.5 border-b border-border-default/60 text-[10px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
                  {t('analytics.portfolioPickerHeading', { defaultValue: 'Portföylerim' })}
                </div>
                {userPortfolios.map((p) => {
                  const code = String(p.id);
                  const alreadyAdded = selected.some((s) => s.code === code && s.type === 'PORTFOLIO');
                  return (
                    <button
                      key={p.id}
                      type="button"
                      disabled={alreadyAdded}
                      onClick={() => addPortfolio(p)}
                      className={`w-full text-left px-3 py-2 text-xs flex items-center gap-2 border-none bg-transparent transition-colors ${
                        alreadyAdded
                          ? 'text-fg-subtle cursor-not-allowed'
                          : 'text-fg hover:bg-accent/10 hover:text-accent cursor-pointer'
                      }`}
                    >
                      <Briefcase className="h-3 w-3 shrink-0" />
                      <span className="flex-1 truncate">{p.name}</span>
                      {alreadyAdded && <span className="text-[9px] font-mono text-fg-subtle">✓</span>}
                    </button>
                  );
                })}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
