import {
  Search, ArrowUp, ArrowDown, RotateCcw,
} from 'lucide-react';
import { SORT_OPTIONS, TYPE_BADGE, RISK_STYLE } from '../../returnsConstants';

export default function ReturnsControls({
  t,
  sortKey, setSortKey, sortDir, setSortDir, setPage,
  resetView, isDefaultView,
  availableTypes, typeFilter, toggleType, setTypeFilterArr,
  riskFilter, toggleRisk, setRiskFilterArr,
  search, setSearch,
  filtered, rows,
}) {
  return (
    <>
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-xs font-display font-semibold text-fg-muted mr-1">
          {t('analytics.returns.sortBy', { defaultValue: 'Sırala' })}
        </span>
        {SORT_OPTIONS.map((opt) => {
          const active = sortKey === opt.id;
          return (
            <button
              key={opt.id}
              type="button"
              onClick={() => {
                if (active) setSortDir(sortDir === 'desc' ? 'asc' : 'desc');
                else { setSortKey(opt.id); setSortDir('desc'); }
                setPage(0);
              }}
              title={opt.id === 'riskAdj' ? t('analytics.returns.riskAdjustedInfo') : undefined}
              className={`inline-flex items-center gap-1 text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 cursor-pointer border-none transition-colors ${
                active ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
              }`}
            >
              {t(opt.labelKey)}
              {active && (sortDir === 'desc' ? <ArrowDown className="h-3 w-3" /> : <ArrowUp className="h-3 w-3" />)}
            </button>
          );
        })}
        <button
          type="button"
          onClick={resetView}
          disabled={isDefaultView}
          title={t('analytics.returns.reset', { defaultValue: 'Sıfırla' })}
          className={`inline-flex items-center gap-1 text-[11px] font-display font-semibold rounded-md px-2 py-1 ml-1 border-none bg-transparent transition-colors ${
            isDefaultView ? 'text-fg-subtle/40 cursor-default' : 'text-fg-subtle hover:text-fg cursor-pointer'
          }`}
        >
          <RotateCcw className="h-3 w-3" />
          {t('analytics.returns.reset', { defaultValue: 'Sıfırla' })}
        </button>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
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
                onClick={() => toggleType(tp)}
                className="text-[11px] font-mono font-semibold tracking-[0.04em] rounded-md px-2 py-1 cursor-pointer border-none transition-all"
                style={active ? {
                  background: `${badge.color}26`, color: badge.color, boxShadow: `inset 0 0 0 1px ${badge.color}66`,
                } : {
                  background: 'transparent', color: 'var(--color-fg-muted)', boxShadow: 'inset 0 0 0 1px var(--color-border-default)',
                }}
              >
                {t(`assets.labels.${tp}`, { defaultValue: badge.label })}
              </button>
            );
          })}
          {typeFilter.size > 0 && (
            <button
              type="button"
              onClick={() => { setPage(0); setTypeFilterArr([]); }}
              className="text-xs font-display font-semibold text-fg-subtle hover:text-fg cursor-pointer border-none bg-transparent ml-1"
            >
              {t('analytics.clearFilters', { defaultValue: 'Temizle' })}
            </button>
          )}
        </div>
        <div className="flex items-center gap-1 flex-wrap sm:border-l sm:border-border-default/60 sm:pl-3 sm:ml-1" title={t('analytics.returns.riskInfo')}>
          <span className="text-xs font-display font-semibold text-fg-muted mr-1">
            {t('analytics.returns.risk', { defaultValue: 'Risk' })}
          </span>
          {['LOW', 'MEDIUM', 'HIGH'].map((lvl) => {
            const s = RISK_STYLE[lvl];
            const active = riskFilter.has(lvl);
            return (
              <button
                key={lvl}
                type="button"
                onClick={() => toggleRisk(lvl)}
                className={`text-[11px] font-mono font-semibold tracking-[0.04em] rounded-md px-2 py-1 cursor-pointer border transition-all ${
                  active ? s.chip : s.idle
                }`}
              >
                {t(s.key)}
              </button>
            );
          })}
          {riskFilter.size > 0 && (
            <button
              type="button"
              onClick={() => { setPage(0); setRiskFilterArr([]); }}
              className="text-xs font-display font-semibold text-fg-subtle hover:text-fg cursor-pointer border-none bg-transparent ml-1"
            >
              {t('analytics.clearFilters', { defaultValue: 'Temizle' })}
            </button>
          )}
        </div>
        <div className="relative w-full sm:w-auto sm:flex-1 sm:min-w-[200px] sm:max-w-md">
          <span className="absolute inset-y-0 left-3 flex items-center pointer-events-none">
            <Search className="h-3.5 w-3.5 text-fg-muted" />
          </span>
          <input
            type="text"
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            maxLength={64}
            placeholder={t('analytics.searchAsset', { defaultValue: 'Asset ara — kaçıncı sırada?' })}
            className="w-full rounded-lg border border-border-default bg-bg-elevated pl-9 pr-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 transition-colors"
          />
        </div>
        <span className="text-[10px] font-mono uppercase tracking-[0.16em] text-fg-subtle tabular-nums">
          {filtered.length} / {rows.length}
        </span>
      </div>
    </>
  );
}
