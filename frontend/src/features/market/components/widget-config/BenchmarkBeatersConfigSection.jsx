import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Trophy, ArrowDownAZ, ArrowUpAZ } from 'lucide-react';
import PopoverHeader from './PopoverHeader';
import BenchmarkPicker from '../../../analytics/components/BenchmarkPicker';
import { useMacroIndicators } from '../../../macro/hooks/useMacroIndicators';

const TYPE_VALUES = ['SPOT', 'CRYPTO', 'FOREX', 'FUND', 'COMMODITY', 'DEPOSIT'];
const VERDICT_VALUES = ['ALL', 'WINNERS', 'LOSERS'];
const PERIOD_OPTIONS = [
  { id: '1M', labelKey: 'periodOneMonth' },
  { id: '3M', labelKey: 'periodThreeMonths' },
  { id: '6M', labelKey: 'periodSixMonths' },
  { id: '1Y', labelKey: 'periodOneYear' },
  { id: '5Y', labelKey: 'periodFiveYears' },
];
const BENCHMARK_CATEGORIES = ['INFLATION', 'RATES', 'DEPOSIT'];
const MIN_LIMIT = 5;
const MAX_LIMIT = 20;

function parseAssetTypes(raw) {
  if (Array.isArray(raw)) return new Set(raw.map((s) => String(s).toUpperCase()));
  if (typeof raw === 'string') {
    return new Set(raw.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean));
  }
  return new Set();
}

export default function BenchmarkBeatersConfigSection({ config, onChange }) {
  const { t } = useTranslation();
  const { data: macroList = [] } = useMacroIndicators();
  const verdict = (config?.verdict || 'ALL').toUpperCase();
  const period = (config?.period || '1Y').toUpperCase();
  const limit = Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, parseInt(config?.limit ?? 10, 10) || 10));
  const sortDir = (config?.sortDir || 'DESC').toUpperCase();
  const benchmark = config?.benchmarkCode || '';

  const selectedTypes = useMemo(() => parseAssetTypes(config?.assetType), [config?.assetType]);
  const benchmarkOptions = useMemo(
    () => macroList.filter((m) => BENCHMARK_CATEGORIES.includes(m.category)),
    [macroList],
  );

  const setField = (key, value) => onChange({ ...config, [key]: value });

  const toggleType = (value) => {
    const next = new Set(selectedTypes);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    setField('assetType', Array.from(next));
  };

  const clearTypes = () => setField('assetType', []);

  const allTypesSelected = selectedTypes.size === 0;

  return (
    <>
      <PopoverHeader Icon={Trophy} title={t('widgetSettings.beatersHeader', { defaultValue: 'Gösterge Sıralaması' })} />

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.beatersBenchmark', { defaultValue: 'Gösterge' })}
      </label>
      <div className="mb-3">
        <BenchmarkPicker
          value={benchmark}
          onChange={(v) => setField('benchmarkCode', v)}
          options={benchmarkOptions}
          t={t}
          defaultLabel={t('analytics.benchmarkDefault', { defaultValue: 'Varsayılan (TÜFE)' })}
        />
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.beatersVerdict', { defaultValue: 'Sonuç' })}
      </label>
      <div className="flex gap-1.5 mb-3">
        {VERDICT_VALUES.map((value) => {
          const active = verdict === value;
          return (
            <button
              key={value}
              type="button"
              onClick={() => setField('verdict', value)}
              className={`flex-1 font-display text-[11px] tracking-tight font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer
                ${active
                  ? 'border-accent bg-accent/15 text-accent shadow-[inset_0_0_10px_-3px_var(--color-accent)]'
                  : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5'}`}
            >
              {t(`widgetSettings.beatersVerdictValues.${value}`, { defaultValue: value })}
            </button>
          );
        })}
      </div>

      <label className="flex items-center justify-between font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        <span>{t('widgetSettings.beatersAssetType', { defaultValue: 'Varlık tipi' })}</span>
        {!allTypesSelected && (
          <button
            type="button"
            onClick={clearTypes}
            className="text-[9px] tracking-[0.16em] uppercase text-fg-subtle hover:text-fg cursor-pointer border-none bg-transparent"
          >
            {t('analytics.clearFilters', { defaultValue: 'Tümü' })}
          </button>
        )}
      </label>
      <div className="flex flex-wrap gap-1 mb-3">
        {TYPE_VALUES.map((value) => {
          const active = selectedTypes.has(value);
          return (
            <button
              key={value}
              type="button"
              onClick={() => toggleType(value)}
              className={`font-display text-[10px] tracking-tight font-semibold px-2 py-0.5 rounded-md border transition-all cursor-pointer
                ${active
                  ? 'border-accent bg-accent/15 text-accent'
                  : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5'}`}
            >
              {t(`widgetSettings.beatersTypeValues.${value}`, { defaultValue: value })}
            </button>
          );
        })}
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.beatersPeriod', { defaultValue: 'Dönem' })}
      </label>
      <div className="flex gap-1 mb-3">
        {PERIOD_OPTIONS.map((opt) => {
          const active = period === opt.id;
          return (
            <button
              key={opt.id}
              type="button"
              onClick={() => setField('period', opt.id)}
              className={`flex-1 font-mono text-[10px] tracking-wide font-semibold px-1.5 py-1 rounded-md border transition-all cursor-pointer
                ${active
                  ? 'border-accent bg-accent/15 text-accent'
                  : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5'}`}
            >
              {t(`analytics.${opt.labelKey}`, { defaultValue: opt.id })}
            </button>
          );
        })}
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.beatersSortDir', { defaultValue: 'Sıralama' })}
      </label>
      <div className="flex gap-1.5 mb-3">
        <button
          type="button"
          onClick={() => setField('sortDir', 'DESC')}
          className={`flex-1 flex items-center justify-center gap-1.5 font-display text-[11px] tracking-tight font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer
            ${sortDir === 'DESC'
              ? 'border-accent bg-accent/15 text-accent'
              : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5'}`}
        >
          <ArrowDownAZ className="h-3 w-3" />
          {t('widgetSettings.beatersSortDesc', { defaultValue: 'En iyiden' })}
        </button>
        <button
          type="button"
          onClick={() => setField('sortDir', 'ASC')}
          className={`flex-1 flex items-center justify-center gap-1.5 font-display text-[11px] tracking-tight font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer
            ${sortDir === 'ASC'
              ? 'border-accent bg-accent/15 text-accent'
              : 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5'}`}
        >
          <ArrowUpAZ className="h-3 w-3" />
          {t('widgetSettings.beatersSortAsc', { defaultValue: 'En kötüden' })}
        </button>
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.beatersLimit', { defaultValue: 'Liste uzunluğu' })}
        <span className="ml-1 text-accent font-bold tabular-nums">{limit}</span>
      </label>
      <input
        type="range"
        min={MIN_LIMIT}
        max={MAX_LIMIT}
        step={1}
        value={limit}
        onChange={(e) => setField('limit', parseInt(e.target.value, 10))}
        className="w-full accent-accent cursor-pointer"
      />
      <div className="flex justify-between font-mono text-[9px] text-fg-subtle tabular-nums mt-1">
        <span>{MIN_LIMIT}</span>
        <span>{MAX_LIMIT}</span>
      </div>
    </>
  );
}
