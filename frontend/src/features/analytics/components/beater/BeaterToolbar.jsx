import { Search, ArrowUp, ArrowDown, RotateCcw } from 'lucide-react';
import { SORT_OPTIONS, TYPE_BADGE } from '../../inflationBeaterConstants';

export default function BeaterToolbar({
  t, sortKey, sortDir, onToggleSort, onReset, isDefaultView,
  verdictFilter, onVerdictChange, availableTypes, typeFilter, onTypeToggle, onClearTypes,
  search, onSearchChange, filteredCount, totalCount,
}) {
  return (
    <>
      <div className="flex items-center gap-1.5 flex-wrap pt-1">
        <span className="text-xs font-display font-semibold text-fg-muted mr-1">
          {t('analytics.sortBy', { defaultValue: 'Sırala' })}
        </span>
        {SORT_OPTIONS.map((opt) => {
          const active = sortKey === opt.id;
          return (
            <button
              key={opt.id}
              type="button"
              onClick={() => onToggleSort(opt.id)}
              className={`inline-flex items-center gap-1 text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 cursor-pointer border-none transition-colors ${
                active ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
              }`}
            >
              {t(opt.labelKey)}
              {active && (sortDir === 'asc' ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" />)}
            </button>
          );
        })}
        <button
          type="button"
          onClick={onReset}
          disabled={isDefaultView}
          title={t('analytics.reset', { defaultValue: 'Sıfırla' })}
          className={`inline-flex items-center gap-1 text-[11px] font-display font-semibold rounded-md px-2 py-1 ml-1 border-none bg-transparent transition-colors ${
            isDefaultView ? 'text-fg-subtle/40 cursor-default' : 'text-fg-subtle hover:text-fg cursor-pointer'
          }`}
        >
          <RotateCcw className="h-3 w-3" />
          {t('analytics.reset', { defaultValue: 'Sıfırla' })}
        </button>
      </div>

      <div className="flex flex-wrap items-center gap-3 pt-1">
        <div className="flex items-center gap-1">
          <span className="text-xs font-display font-semibold text-fg-muted mr-1">
            {t('analytics.verdictFilter', { defaultValue: 'Durum' })}
          </span>
          {['all', 'beats', 'losers'].map((v) => (
            <button
              key={v}
              type="button"
              onClick={() => onVerdictChange(v)}
              className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 cursor-pointer border transition-colors ${
                verdictFilter === v
                  ? v === 'beats' ? 'bg-success/15 text-success border-success/40'
                    : v === 'losers' ? 'bg-danger/15 text-danger border-danger/40'
                    : 'bg-accent/15 text-accent border-accent/40'
                  : 'text-fg-muted border-transparent hover:text-fg'
              }`}
            >
              {t(`analytics.verdict_${v}`, { defaultValue: v })}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-1 flex-wrap">
          <span className="text-xs font-display font-semibold text-fg-muted mr-1">
            {t('analytics.typeFilter', { defaultValue: 'Tip' })}
          </span>
          {availableTypes.map((tp) => {
            const badge = TYPE_BADGE[tp] || { label: tp, color: '#6366f1' };
            const active = typeFilter.has(tp);
            return (
              <button
                key={tp}
                type="button"
                onClick={() => onTypeToggle(tp)}
                className="text-[11px] font-mono font-semibold tracking-[0.04em] rounded-md px-2 py-1 cursor-pointer border-none transition-all"
                style={active ? {
                  background: `${badge.color}26`,
                  color: badge.color,
                  boxShadow: `inset 0 0 0 1px ${badge.color}66`,
                } : {
                  background: 'transparent',
                  color: 'var(--color-fg-muted)',
                  boxShadow: `inset 0 0 0 1px var(--color-border-default)`,
                }}
              >
                {t(`assets.labels.${tp}`, { defaultValue: badge.label })}
              </button>
            );
          })}
          {typeFilter.size > 0 && (
            <button
              type="button"
              onClick={() => onClearTypes()}
              className="text-xs font-display font-semibold text-fg-subtle hover:text-fg cursor-pointer border-none bg-transparent ml-1"
            >
              {t('analytics.clearFilters', { defaultValue: 'Temizle' })}
            </button>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        <div className="relative flex-1 min-w-0 sm:min-w-[200px] max-w-md">
          <span className="absolute inset-y-0 left-3 flex items-center pointer-events-none">
            <Search className="h-3.5 w-3.5 text-fg-muted" />
          </span>
          <input
            type="text"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            maxLength={64}
            placeholder={t('analytics.searchAsset', { defaultValue: 'Asset ara — kaçıncı sırada?' })}
            className="w-full rounded-lg border border-border-default bg-bg-elevated pl-9 pr-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 transition-colors"
          />
        </div>
        <span className="text-[10px] font-mono uppercase tracking-[0.16em] text-fg-subtle tabular-nums">
          {filteredCount} / {totalCount}
        </span>
      </div>
    </>
  );
}
