import { useTranslation } from 'react-i18next';
import { X, Search, Target, LineChart, BarChart3 } from 'lucide-react';
import PopoverHeader from './PopoverHeader';
import SearchSuggestions from '../../../../shared/components/form/SearchSuggestions';

const RANGES = ['1W', '1M', '3M', '6M', '1Y', '5Y'];

export default function SingleAssetConfigSection({ config, onChange }) {
  const { t } = useTranslation();
  const selected = config?.code && config?.type ? { code: config.code, type: config.type } : null;
  const showChart = config?.showChart !== false;
  const chartType = config?.chartType === 'candle' ? 'candle' : 'line';
  const range = RANGES.includes(config?.range) ? config.range : '1M';

  const pick = (asset) => onChange({ ...config, type: asset.type, code: asset.code });
  const clear = () => onChange({ ...config, type: undefined, code: undefined });
  const set = (next) => onChange({ ...config, ...next });

  return (
    <div className="space-y-3 min-h-0 flex-1 flex flex-col">
      <PopoverHeader Icon={Target} title={t('singleAssetConfig.title')} />

      {selected ? (
        <div className="flex items-center justify-between gap-2 rounded-lg border border-accent/40 bg-accent/10 px-3 py-2">
          <div className="flex items-center gap-2 min-w-0">
            <span className="font-mono text-[11px] uppercase tracking-[0.08em] font-semibold text-accent">{selected.code.replace('.IS', '')}</span>
            <span className="text-[9px] font-mono uppercase tracking-wider text-accent/70">{selected.type}</span>
          </div>
          <button
            type="button"
            onClick={clear}
            aria-label={t('singleAssetConfig.clear')}
            className="flex items-center justify-center w-6 h-6 rounded-md text-fg-muted hover:text-danger hover:bg-danger/10 transition-colors bg-transparent border-none cursor-pointer"
          >
            <X className="h-3 w-3" />
          </button>
        </div>
      ) : (
        <div className="text-[11px] text-fg-subtle italic">{t('singleAssetConfig.empty')}</div>
      )}

      <div className="flex flex-col gap-1.5">
        <div className="flex items-center gap-1.5 text-[10px] font-mono uppercase tracking-[0.12em] text-fg-muted">
          <Search className="h-3 w-3" />
          {t('singleAssetConfig.searchLabel')}
        </div>
        <SearchSuggestions onSelect={pick} placeholder={t('singleAssetConfig.searchPlaceholder')} />
      </div>

      <div className="pt-2 border-t border-border-default/40 space-y-2.5">
        <label className="flex items-center justify-between gap-2 text-[11px] cursor-pointer">
          <span className="font-mono uppercase tracking-wider text-fg-muted">{t('singleAssetConfig.showChart')}</span>
          <input
            type="checkbox"
            checked={showChart}
            onChange={(e) => set({ showChart: e.target.checked })}
            className="h-4 w-4 cursor-pointer accent-accent"
          />
        </label>

        {showChart && (
          <>
            <div className="space-y-1">
              <div className="text-[10px] font-mono uppercase tracking-wider text-fg-muted">{t('singleAssetConfig.range')}</div>
              <div className="flex items-center gap-0.5 rounded-md border border-border-default/60 bg-bg-base/40 p-0.5">
                {RANGES.map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => set({ range: r })}
                    className={`flex-1 px-1 py-1 text-[10px] font-mono uppercase tracking-wider rounded transition-colors cursor-pointer ${
                      range === r ? 'bg-accent/20 text-accent' : 'text-fg-muted hover:text-fg'
                    }`}
                  >
                    {r}
                  </button>
                ))}
              </div>
            </div>

            <div className="space-y-1">
              <div className="text-[10px] font-mono uppercase tracking-wider text-fg-muted">{t('singleAssetConfig.chartType')}</div>
              <div className="flex items-center gap-0.5 rounded-md border border-border-default/60 bg-bg-base/40 p-0.5">
                <button
                  type="button"
                  onClick={() => set({ chartType: 'line' })}
                  className={`flex-1 flex items-center justify-center gap-1.5 py-1 text-[10px] font-mono uppercase tracking-wider rounded transition-colors cursor-pointer ${
                    chartType === 'line' ? 'bg-accent/20 text-accent' : 'text-fg-muted hover:text-fg'
                  }`}
                >
                  <LineChart className="h-3 w-3" />
                  {t('singleAssetSection.lineChart')}
                </button>
                <button
                  type="button"
                  onClick={() => set({ chartType: 'candle' })}
                  className={`flex-1 flex items-center justify-center gap-1.5 py-1 text-[10px] font-mono uppercase tracking-wider rounded transition-colors cursor-pointer ${
                    chartType === 'candle' ? 'bg-accent/20 text-accent' : 'text-fg-muted hover:text-fg'
                  }`}
                >
                  <BarChart3 className="h-3 w-3" />
                  {t('singleAssetSection.candleChart')}
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
