import { RETURN_PERIODS } from '../../constants';

export default function ReturnsPeriodBar({ t, period, setPeriod, setPage }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      <span className="text-xs font-display font-semibold text-fg-muted mr-1">
        {t('analytics.period', { defaultValue: 'Dönem' })}
      </span>
      {RETURN_PERIODS.map((p) => (
        <button
          key={p.id}
          type="button"
          onClick={() => { setPeriod(p.id); setPage(0); }}
          className={`text-xs font-mono font-semibold rounded-lg px-3 py-1.5 cursor-pointer border-none transition-colors ${
            period === p.id ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
          }`}
        >
          {t(`analytics.${p.labelKey}`, { defaultValue: p.id })}
        </button>
      ))}
    </div>
  );
}
