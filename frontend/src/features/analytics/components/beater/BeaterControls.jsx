import BenchmarkPicker from '../BenchmarkPicker';
import { PERIODS } from '../../constants';

export default function BeaterControls({
  t, period, onPeriodChange, benchmark, onBenchmarkChange, benchmarkOptions,
}) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-xs font-display font-semibold text-fg-muted mr-1">
          {t('analytics.period', { defaultValue: 'Dönem' })}
        </span>
        {PERIODS.map((p) => (
          <button
            key={p.id}
            type="button"
            onClick={() => onPeriodChange(p.id)}
            className={`text-xs font-mono font-semibold rounded-lg px-3 py-1.5 cursor-pointer border-none transition-colors ${
              period === p.id ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
            }`}
          >
            {t(`analytics.${p.labelKey}`, { defaultValue: p.id })}
          </button>
        ))}
      </div>

      <div className="flex items-center gap-2 flex-wrap min-w-0">
        <span className="text-xs font-display font-semibold text-fg-muted">
          {t('analytics.benchmark', { defaultValue: 'Karşılaştırma' })}
        </span>
        <BenchmarkPicker
          value={benchmark}
          onChange={onBenchmarkChange}
          options={benchmarkOptions}
          t={t}
          defaultLabel={t('analytics.benchmarkDefault', { defaultValue: 'TÜFE (varsayılan)' })}
        />
      </div>
    </div>
  );
}
