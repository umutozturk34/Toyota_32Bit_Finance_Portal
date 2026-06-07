import { useMemo, useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Coins, ArrowDownAZ, ArrowUpAZ } from 'lucide-react';
import PopoverHeader from './PopoverHeader';

const PERIOD_OPTIONS = [
  { id: '1W', labelKey: 'periodOneWeek' },
  { id: '1M', labelKey: 'periodOneMonth' },
  { id: '3M', labelKey: 'periodThreeMonths' },
  { id: '6M', labelKey: 'periodSixMonths' },
  { id: '1Y', labelKey: 'periodOneYear' },
  { id: '3Y', labelKey: 'periodThreeYears' },
  { id: '5Y', labelKey: 'periodFiveYears' },
];
const CURRENCY_VALUES = ['TRY', 'USD', 'EUR'];
const CURRENCY_SYMBOL = { TRY: '₺', USD: '$', EUR: '€' };
const SORT_VALUES = ['RETURN', 'RISK_ADJ', 'VOLATILITY'];
const TYPE_VALUES = ['SPOT', 'CRYPTO', 'FOREX', 'FUND', 'COMMODITY'];
const RISK_VALUES = ['LOW', 'MEDIUM', 'HIGH'];
const MIN_LIMIT = 5;
const MAX_LIMIT = 20;

const CHIP_IDLE = 'border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/50 hover:text-accent hover:bg-accent/5';
const CHIP_ACTIVE = 'border-accent bg-accent/15 text-accent';

function parseSet(raw) {
  if (Array.isArray(raw)) return new Set(raw.map((s) => String(s).toUpperCase()));
  if (typeof raw === 'string') {
    return new Set(raw.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean));
  }
  return new Set();
}

export default function ReturnsConfigSection({ config, onChange }) {
  const { t } = useTranslation();
  const period = (config?.period || '1Y').toUpperCase();
  const currency = (config?.currency || 'TRY').toUpperCase();
  const sortBy = (config?.sortBy || 'RETURN').toUpperCase();
  const sortDir = (config?.sortDir || 'DESC').toUpperCase();
  const limit = Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, parseInt(config?.limit ?? 10, 10) || 10));
  // Drive the range thumb from local state during the drag and COMMIT only on release: setField rewrites the
  // whole sections array (which re-renders the react-grid-layout canvas), so committing on every tick made the
  // grid re-render on each pixel and the thumb stutter/stick. Resync when the committed value changes elsewhere.
  const [limitDraft, setLimitDraft] = useState(limit);
  useEffect(() => { setLimitDraft(limit); }, [limit]);

  const selectedTypes = useMemo(() => parseSet(config?.assetType), [config?.assetType]);
  const selectedRisks = useMemo(() => parseSet(config?.risk), [config?.risk]);

  const setField = (key, value) => onChange({ ...config, [key]: value });

  const toggle = (key, current, value) => {
    const next = new Set(current);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    setField(key, Array.from(next));
  };

  return (
    <>
      <PopoverHeader Icon={Coins} title={t('widgetSettings.returnsHeader', { defaultValue: 'Getiri Sıralaması' })} />

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.returnsPeriod', { defaultValue: 'Dönem' })}
      </label>
      <div className="flex flex-wrap gap-1 mb-3">
        {PERIOD_OPTIONS.map((opt) => {
          const active = period === opt.id;
          return (
            <button
              key={opt.id}
              type="button"
              onClick={() => setField('period', opt.id)}
              className={`font-mono text-[10px] tracking-wide font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer ${active ? CHIP_ACTIVE : CHIP_IDLE}`}
            >
              {t(`analytics.${opt.labelKey}`, { defaultValue: opt.id })}
            </button>
          );
        })}
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.returnsCurrency', { defaultValue: 'Para birimi' })}
      </label>
      <div className="flex gap-1.5 mb-3">
        {CURRENCY_VALUES.map((value) => {
          const active = currency === value;
          return (
            <button
              key={value}
              type="button"
              onClick={() => setField('currency', value)}
              className={`flex-1 font-mono text-[11px] tracking-wide font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer ${active ? CHIP_ACTIVE : CHIP_IDLE}`}
            >
              {CURRENCY_SYMBOL[value]} {value}
            </button>
          );
        })}
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.returnsSortBy', { defaultValue: 'Sırala' })}
      </label>
      <div className="flex gap-1.5 mb-3">
        {SORT_VALUES.map((value) => {
          const active = sortBy === value;
          return (
            <button
              key={value}
              type="button"
              onClick={() => setField('sortBy', value)}
              className={`flex-1 font-display text-[11px] tracking-tight font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer ${active ? CHIP_ACTIVE : CHIP_IDLE}`}
            >
              {t(`widgetSettings.returnsSortValues.${value}`, { defaultValue: value })}
            </button>
          );
        })}
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.returnsSortDir', { defaultValue: 'Yön' })}
      </label>
      <div className="flex gap-1.5 mb-3">
        <button
          type="button"
          onClick={() => setField('sortDir', 'DESC')}
          className={`flex-1 flex items-center justify-center gap-1.5 font-display text-[11px] tracking-tight font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer ${sortDir === 'DESC' ? CHIP_ACTIVE : CHIP_IDLE}`}
        >
          <ArrowDownAZ className="h-3 w-3" />
          {t('widgetSettings.returnsSortDesc', { defaultValue: 'Yüksekten' })}
        </button>
        <button
          type="button"
          onClick={() => setField('sortDir', 'ASC')}
          className={`flex-1 flex items-center justify-center gap-1.5 font-display text-[11px] tracking-tight font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer ${sortDir === 'ASC' ? CHIP_ACTIVE : CHIP_IDLE}`}
        >
          <ArrowUpAZ className="h-3 w-3" />
          {t('widgetSettings.returnsSortAsc', { defaultValue: 'Düşükten' })}
        </button>
      </div>

      <label className="flex items-center justify-between font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        <span>{t('widgetSettings.returnsAssetType', { defaultValue: 'Varlık tipi' })}</span>
        {selectedTypes.size > 0 && (
          <button
            type="button"
            onClick={() => setField('assetType', [])}
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
              onClick={() => toggle('assetType', selectedTypes, value)}
              className={`font-display text-[10px] tracking-tight font-semibold px-2 py-0.5 rounded-md border transition-all cursor-pointer ${active ? CHIP_ACTIVE : CHIP_IDLE}`}
            >
              {t(`widgetSettings.returnsTypeValues.${value}`, { defaultValue: value })}
            </button>
          );
        })}
      </div>

      <label className="flex items-center justify-between font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        <span>{t('widgetSettings.returnsRisk', { defaultValue: 'Risk' })}</span>
        {selectedRisks.size > 0 && (
          <button
            type="button"
            onClick={() => setField('risk', [])}
            className="text-[9px] tracking-[0.16em] uppercase text-fg-subtle hover:text-fg cursor-pointer border-none bg-transparent"
          >
            {t('analytics.clearFilters', { defaultValue: 'Tümü' })}
          </button>
        )}
      </label>
      <div className="flex gap-1.5 mb-3">
        {RISK_VALUES.map((value) => {
          const active = selectedRisks.has(value);
          return (
            <button
              key={value}
              type="button"
              onClick={() => toggle('risk', selectedRisks, value)}
              className={`flex-1 font-display text-[11px] tracking-tight font-semibold px-2 py-1 rounded-md border transition-all cursor-pointer ${active ? CHIP_ACTIVE : CHIP_IDLE}`}
            >
              {t(`widgetSettings.returnsRiskValues.${value}`, { defaultValue: value })}
            </button>
          );
        })}
      </div>

      <label className="block font-mono text-[9px] tracking-[0.18em] uppercase text-fg-subtle mb-1.5">
        {t('widgetSettings.returnsLimit', { defaultValue: 'Liste uzunluğu' })}
        <span className="ml-1 text-accent font-bold tabular-nums">{limitDraft}</span>
      </label>
      <input
        type="range"
        min={MIN_LIMIT}
        max={MAX_LIMIT}
        step={1}
        value={limitDraft}
        onChange={(e) => setLimitDraft(parseInt(e.target.value, 10))}
        onPointerUp={(e) => setField('limit', parseInt(e.target.value, 10))}
        onKeyUp={(e) => setField('limit', parseInt(e.target.value, 10))}
        className="w-full accent-accent cursor-pointer"
      />
      <div className="flex justify-between font-mono text-[9px] text-fg-subtle tabular-nums mt-1">
        <span>{MIN_LIMIT}</span>
        <span>{MAX_LIMIT}</span>
      </div>
    </>
  );
}
