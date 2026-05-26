import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useNavigate, useSearchParams } from 'react-router-dom';
import useSessionState from '../../../shared/hooks/useSessionState';
import { TrendingUp, TrendingDown, Trophy, Search, ChevronLeft, ChevronRight, GitCompare, ArrowUp, ArrowDown } from 'lucide-react';
import Card from '../../../shared/components/card';
import LoadingState from '../../../shared/components/feedback/LoadingState';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import { useInflationBeaters } from '../hooks/useAnalytics';
import { useMacroIndicators } from '../../macro/hooks/useMacroIndicators';
import { useMoney } from '../../../shared/hooks/useMoney';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';
import BenchmarkPicker from '../components/BenchmarkPicker';
import { PERIODS } from '../constants';
import { formatPercent } from '../utils';

const PAGE_SIZE = 10;
const FIXED_TYPE_ORDER = ['SPOT', 'CRYPTO', 'FUND', 'FOREX', 'COMMODITY', 'DEPOSIT', 'MACRO'];
const BENCHMARK_CATEGORIES = ['INFLATION', 'RATES', 'DEPOSIT'];
const MACRO_CATEGORY_TO_MARKET_TYPE = {
  DEPOSIT: 'MACRO_DEPOSIT',
  INFLATION: 'MACRO_INFLATION',
  RATES: 'MACRO_RATE',
};
const ANALYTICS_TO_MARKET_TYPE = {
  SPOT: 'STOCK',
  CRYPTO: 'CRYPTO',
  FOREX: 'FOREX',
  FUND: 'FUND',
  COMMODITY: 'COMMODITY',
  VIOP: 'VIOP',
  BOND: 'BOND',
  DEPOSIT: 'MACRO_DEPOSIT',
};
const TYPE_BADGE = {
  SPOT:      { label: 'BIST',      color: '#5E6AD2' },
  CRYPTO:    { label: 'CRYPTO',    color: '#f97316' },
  FOREX:     { label: 'FOREX',     color: '#06b6d4' },
  FUND:      { label: 'FUND',      color: '#8b5cf6' },
  COMMODITY: { label: 'COMMODITY', color: '#f59e0b' },
  VIOP:      { label: 'VIOP',      color: '#ef4444' },
  BOND:      { label: 'BOND',      color: '#d946ef' },
  DEPOSIT:   { label: 'DEPOSIT',   color: '#10b981' },
  MACRO:     { label: 'MACRO',     color: '#0ea5e9' },
};

export default function InflationBeaterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const [period, setPeriod] = useSessionState('beater:period', params.get('bp') || '1Y');
  const [benchmark, setBenchmark] = useSessionState('beater:benchmark', params.get('bb') || '');
  const [search, setSearch] = useSessionState('beater:search', params.get('bs') || '');
  const [page, setPage] = useSessionState('beater:page', Number(params.get('bpage')) || 0);
  const [verdictFilter, setVerdictFilter] = useSessionState('beater:verdict', params.get('bv') || 'all');
  const [typeFilterArr, setTypeFilterArr] = useSessionState('beater:type',
    (params.get('bt') || '').split(',').filter(Boolean));
  const [sortKey, setSortKey] = useSessionState('beater:sortKey', params.get('bsk') || 'rank');
  const [sortDir, setSortDir] = useSessionState('beater:sortDir', params.get('bsd') || 'asc');
  const typeFilter = useMemo(() => new Set(typeFilterArr), [typeFilterArr]);
  const setTypeFilter = (updater) => {
    const next = typeof updater === 'function' ? updater(typeFilter) : updater;
    setTypeFilterArr(Array.from(next instanceof Set ? next : new Set(next)));
  };

  useEffect(() => {
    const next = new URLSearchParams(params);
    next.set('tab', 'beaters');
    if (period && period !== '1Y') next.set('bp', period); else next.delete('bp');
    if (benchmark) next.set('bb', benchmark); else next.delete('bb');
    if (search) next.set('bs', search); else next.delete('bs');
    if (page > 0) next.set('bpage', String(page)); else next.delete('bpage');
    if (verdictFilter && verdictFilter !== 'all') next.set('bv', verdictFilter); else next.delete('bv');
    if (typeFilter.size > 0) next.set('bt', Array.from(typeFilter).join(',')); else next.delete('bt');
    if (sortKey && sortKey !== 'rank') next.set('bsk', sortKey); else next.delete('bsk');
    if (sortDir && sortDir !== 'asc') next.set('bsd', sortDir); else next.delete('bsd');
    setParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period, benchmark, search, page, verdictFilter, typeFilter, sortKey, sortDir]);

  const toggleSort = (key) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir(key === 'rank' ? 'asc' : 'desc');
    }
    setPage(0);
  };

  const { currency: displayCurrency } = useMoney();
  const targetCurrencyOverride = displayCurrency === 'ORIGINAL' ? null : displayCurrency;
  const { data, isLoading, isError, refetch } = useInflationBeaters(period, benchmark, targetCurrencyOverride);
  const { data: macroList = [] } = useMacroIndicators();

  const benchmarkOptions = useMemo(
    () => macroList.filter((m) => BENCHMARK_CATEGORIES.includes(m.category)),
    [macroList]
  );

  function resolveMarketType(entry) {
    if (entry.type === 'MACRO') {
      const macro = macroList.find((m) => m.code === entry.code);
      return macro ? (MACRO_CATEGORY_TO_MARKET_TYPE[macro.category] || 'MACRO_RATE') : 'MACRO_RATE';
    }
    return ANALYTICS_TO_MARKET_TYPE[entry.type] || entry.type;
  }

  function compareWithBenchmark(entry) {
    const benchmarkInd = data?.benchmarkCode
      ? macroList.find((m) => m.code === data.benchmarkCode)
      : null;
    const benchmarkType = benchmarkInd
      ? MACRO_CATEGORY_TO_MARKET_TYPE[benchmarkInd.category] || 'MACRO_RATE'
      : 'MACRO_INFLATION';
    const codes = [entry.code];
    const types = [resolveMarketType(entry)];
    if (data?.benchmarkCode) {
      codes.push(data.benchmarkCode);
      types.push(benchmarkType);
    }
    const next = new URLSearchParams({
      tab: 'compare',
      codes: codes.join(','),
      types: types.join(','),
      range: period,
      from: 'beaters',
    });
    navigate({ search: `?${next.toString()}` });
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="space-y-6"
    >
      <header className="pb-3 border-b border-border-default/40">
        <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg tracking-tight leading-none">
          {t('analytics.beaterTitle', { defaultValue: 'Benchmark Yenenler' })}
        </h1>
        <p className="mt-2 text-sm text-fg-muted max-w-2xl">
          {t('analytics.beaterSubtitle', {
            defaultValue: 'Bir indikatör seç ve hangi enstrümanların onu geçtiğini gör — TÜFE, politika faizi, mevduat veya başka bir gösterge.',
          })}
        </p>
      </header>

      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-xs font-display font-semibold text-fg-muted mr-1">
            {t('analytics.period', { defaultValue: 'Dönem' })}
          </span>
          {PERIODS.map((p) => (
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

        <div className="flex items-center gap-2 flex-wrap min-w-0">
          <span className="text-xs font-display font-semibold text-fg-muted">
            {t('analytics.benchmark', { defaultValue: 'Karşılaştırma' })}
          </span>
          <BenchmarkPicker
            value={benchmark}
            onChange={(v) => { setBenchmark(v); setPage(0); }}
            options={benchmarkOptions}
            t={t}
            defaultLabel={t('analytics.benchmarkDefault', { defaultValue: 'TÜFE (varsayılan)' })}
          />
        </div>
      </div>

      {isLoading && <LoadingState message={t('analytics.loading', { defaultValue: 'Hesaplanıyor...' })} />}
      {isError && <ErrorState message={t('analytics.loadError', { defaultValue: 'Yüklenirken hata oluştu' })} onRetry={refetch} />}
      {data && (
        <Results
          data={data}
          period={period}
          t={t}
          search={search}
          onSearchChange={(v) => { setSearch(v); setPage(0); }}
          page={page}
          onPageChange={setPage}
          verdictFilter={verdictFilter}
          onVerdictChange={(v) => { setVerdictFilter(v); setPage(0); }}
          typeFilter={typeFilter}
          onTypeToggle={(type) => {
            setPage(0);
            setTypeFilter((prev) => {
              const next = new Set(prev);
              if (next.has(type)) next.delete(type);
              else next.add(type);
              return next;
            });
          }}
          onClearTypes={() => {
            setPage(0);
            setTypeFilter(new Set());
          }}
          sortKey={sortKey}
          sortDir={sortDir}
          onToggleSort={toggleSort}
          onCompare={compareWithBenchmark}
        />
      )}
    </motion.div>
  );
}

function Results({ data, period, t, search, onSearchChange, page, onPageChange, onCompare,
                   verdictFilter, onVerdictChange, typeFilter, onTypeToggle, onClearTypes,
                   sortKey, sortDir, onToggleSort }) {
  const indexedEntries = useMemo(
    () => (data.entries || []).map((e, idx) => ({ ...e, _rank: idx + 1 })),
    [data.entries]
  );
  const totalCount = indexedEntries.length;
  const winRate = totalCount > 0 ? Math.round((data.beatingCount / totalCount) * 100) : 0;

  const availableTypes = useMemo(() => {
    const presentSet = new Set();
    indexedEntries.forEach((e) => presentSet.add(e.type));
    const ordered = FIXED_TYPE_ORDER.filter((tp) => presentSet.has(tp));
    presentSet.forEach((tp) => { if (!FIXED_TYPE_ORDER.includes(tp)) ordered.push(tp); });
    return ordered;
  }, [indexedEntries]);

  const filteredEntries = useMemo(() => {
    const q = search.trim().toLowerCase();
    return indexedEntries.filter((e) => {
      if (verdictFilter === 'beats' && !e.beatsBenchmark) return false;
      if (verdictFilter === 'losers' && e.beatsBenchmark) return false;
      if (typeFilter.size > 0 && !typeFilter.has(e.type)) return false;
      if (!q) return true;
      return e.code.toLowerCase().includes(q)
        || (e.name && e.name.toLowerCase().includes(q))
        || e.type.toLowerCase().includes(q);
    });
  }, [indexedEntries, search, verdictFilter, typeFilter]);

  const sortedEntries = useMemo(() => {
    const arr = [...filteredEntries];
    const num = (v) => (v == null ? -Infinity : Number(v));
    const cmpAsc = (a, b) => {
      if (sortKey === 'nominal') return num(a.nominalReturnPct) - num(b.nominalReturnPct);
      if (sortKey === 'excess') return num(a.excessReturnPct) - num(b.excessReturnPct);
      if (sortKey === 'name') return (a.name || a.code).localeCompare(b.name || b.code);
      return a._rank - b._rank;
    };
    arr.sort((a, b) => (sortDir === 'asc' ? cmpAsc(a, b) : -cmpAsc(a, b)));
    return arr;
  }, [filteredEntries, sortKey, sortDir]);

  const totalPages = Math.max(1, Math.ceil(sortedEntries.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const pageEntries = sortedEntries.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  const benchmarkLabel = data.benchmarkLabel
    ? t(`marketOverview.macro.${data.benchmarkLabel}`, { defaultValue: data.benchmarkLabel })
    : t('analytics.cpiGrowth', { defaultValue: 'TÜFE' });

  return (
    <>
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <HeroStat
          icon={<Trophy className="h-4 w-4" />}
          label={t('analytics.beatingBenchmark', { defaultValue: 'Yenenler' })}
          value={`${data.beatingCount}/${totalCount}`}
          sub={`${winRate}%`}
          accent="#10b981"
        />
        <HeroStat
          icon={<TrendingUp className="h-4 w-4" />}
          label={t('analytics.benchmarkReturn', { defaultValue: 'Benchmark getirisi' })}
          value={data.benchmarkReturnPct != null ? formatPercent(data.benchmarkReturnPct) : '—'}
          sub={`${benchmarkLabel} · ${period}${data.comparisonCurrency ? ` · ${data.comparisonCurrency}` : ''}`}
          accent="#f59e0b"
        />
        <HeroStat
          icon={<TrendingDown className="h-4 w-4" />}
          label={t('analytics.losers', { defaultValue: 'Altta kalan' })}
          value={`${totalCount - data.beatingCount}`}
          sub={t('analytics.realLoss', { defaultValue: 'Excess return < 0' })}
          accent="#ef4444"
        />
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
              className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 cursor-pointer border-none transition-colors ${
                verdictFilter === v
                  ? v === 'beats' ? 'bg-emerald-500/15 text-emerald-500 shadow-[inset_0_0_0_1px_rgba(16,185,129,0.4)]'
                    : v === 'losers' ? 'bg-red-500/15 text-red-500 shadow-[inset_0_0_0_1px_rgba(239,68,68,0.4)]'
                    : 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]'
                  : 'text-fg-muted hover:text-fg'
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
            placeholder={t('analytics.searchAsset', { defaultValue: 'Asset ara — kaçıncı sırada?' })}
            className="w-full rounded-lg border border-border-default bg-bg-elevated pl-9 pr-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent focus:ring-1 focus:ring-accent/30 transition-colors"
          />
        </div>
        <span className="text-[10px] font-mono uppercase tracking-[0.16em] text-fg-subtle tabular-nums">
          {filteredEntries.length} / {totalCount}
        </span>
      </div>

      <Card variant="elevated" radius="xl" padding="none" backdropBlur className="overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-bg-elevated/40">
              <tr>
                <Th sortKey="rank" activeSort={sortKey} dir={sortDir} onToggle={onToggleSort}>#</Th>
                <Th sortKey="name" activeSort={sortKey} dir={sortDir} onToggle={onToggleSort}>
                  {t('analytics.instrument', { defaultValue: 'Enstrüman' })}
                </Th>
                <Th align="right" sortKey="nominal" activeSort={sortKey} dir={sortDir} onToggle={onToggleSort}
                    title={t('analytics.nominalReturnTooltip', { defaultValue: 'Mutlak yüzde değişim' })}>
                  {t('analytics.nominalReturn', { defaultValue: 'Nominal' })}
                </Th>
                <Th align="right" sortKey="excess" activeSort={sortKey} dir={sortDir} onToggle={onToggleSort}
                    title={t('analytics.excessReturnTooltip', { defaultValue: 'Nominal − Gösterge' })}>
                  {t('analytics.excessReturn', { defaultValue: 'Gösterge Üzeri' })}
                </Th>
                <Th align="right">{t('analytics.verdict', { defaultValue: 'Sonuç' })}</Th>
              </tr>
            </thead>
            <tbody>
              {pageEntries.map((entry, idx) => (
                <motion.tr
                  key={`${entry.type}|${entry.code}`}
                  initial={{ opacity: 0, x: -8 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: idx * 0.02 }}
                  onClick={() => onCompare?.(entry)}
                  className="group border-t border-border-default/40 hover:bg-bg-elevated/40 transition-colors cursor-pointer"
                  title={t('analytics.openInCompare', { defaultValue: 'Compare’de aç' })}
                >
                  <td className="py-3 px-2 sm:px-3 font-mono text-xs tabular-nums">
                    <span className={entry._rank <= 3 ? 'text-amber-500 font-bold' : 'text-fg-muted'}>
                      {entry._rank}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-fg font-semibold">{instrumentDisplayName(t, entry.type, entry.code, entry.name)}</span>
                      {(() => {
                        const badge = TYPE_BADGE[entry.type] || { label: entry.type, color: '#6366f1' };
                        return (
                          <span
                            className="inline-flex items-center text-[10px] font-mono font-semibold tracking-[0.04em] rounded px-1.5 py-0.5"
                            style={{ background: `${badge.color}1f`, color: badge.color, boxShadow: `inset 0 0 0 1px ${badge.color}40` }}
                          >
                            {t(`assets.labels.${entry.type}`, { defaultValue: badge.label })}
                          </span>
                        );
                      })()}
                      <GitCompare className="h-3 w-3 text-fg-subtle opacity-0 group-hover:opacity-100 transition-opacity" />
                    </div>
                    <div className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle mt-0.5">
                      {entry.code}
                    </div>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right font-mono tabular-nums">
                    <span className={Number(entry.nominalReturnPct) >= 0 ? 'text-emerald-500' : 'text-red-500'}>
                      {formatPercent(entry.nominalReturnPct)}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right font-mono font-bold tabular-nums">
                    <span className={Number(entry.excessReturnPct) >= 0 ? 'text-emerald-500' : 'text-red-500'}>
                      {formatPercent(entry.excessReturnPct)}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right">
                    {entry.beatsBenchmark ? (
                      <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.14em] rounded-md px-2 py-0.5 bg-emerald-500/15 text-emerald-500">
                        <Trophy className="h-3 w-3" />
                        {t('analytics.beats', { defaultValue: 'Yendi' })}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.14em] rounded-md px-2 py-0.5 bg-red-500/10 text-red-500">
                        <TrendingDown className="h-3 w-3" />
                        {t('analytics.lost', { defaultValue: 'Kaybetti' })}
                      </span>
                    )}
                  </td>
                </motion.tr>
              ))}
              {pageEntries.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-6 text-center text-xs text-fg-muted font-mono italic">
                    {t('analytics.noMatch', { defaultValue: 'Eşleşme yok' })}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="flex items-center justify-between gap-2 px-3 py-2 border-t border-border-default/40">
            <span className="text-[10px] font-mono uppercase tracking-[0.14em] text-fg-subtle tabular-nums">
              {safePage + 1} / {totalPages}
            </span>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => onPageChange(Math.max(0, safePage - 1))}
                disabled={safePage === 0}
                className="h-7 w-7 flex items-center justify-center rounded-md text-fg-muted hover:text-fg hover:bg-bg-elevated transition-colors border-none cursor-pointer bg-transparent disabled:opacity-30 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                onClick={() => onPageChange(Math.min(totalPages - 1, safePage + 1))}
                disabled={safePage >= totalPages - 1}
                className="h-7 w-7 flex items-center justify-center rounded-md text-fg-muted hover:text-fg hover:bg-bg-elevated transition-colors border-none cursor-pointer bg-transparent disabled:opacity-30 disabled:cursor-not-allowed"
              >
                <ChevronRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}
      </Card>
    </>
  );
}

function HeroStat({ icon, label, value, sub, accent }) {
  return (
    <div
      className="rounded-xl border px-3 sm:px-4 py-3 sm:py-3.5"
      style={{ background: `${accent}0d`, borderColor: `${accent}33` }}
    >
      <div className="flex items-center gap-2 mb-2 text-xs font-display font-semibold tracking-tight" style={{ color: accent }}>
        {icon}
        <span>{label}</span>
      </div>
      <div className="font-display text-2xl sm:text-3xl font-bold text-fg tabular-nums leading-none">{value}</div>
      <div className="mt-1.5 text-xs font-mono text-fg-subtle">{sub}</div>
    </div>
  );
}

function Th({ children, align = 'left', title, sortKey, activeSort, dir, onToggle }) {
  const sortable = !!(sortKey && onToggle);
  const active = sortable && activeSort === sortKey;
  const indicator = active ? (dir === 'asc' ? <ArrowUp className="inline h-3 w-3 ml-1" /> : <ArrowDown className="inline h-3 w-3 ml-1" />) : null;
  return (
    <th
      title={title}
      onClick={sortable ? () => onToggle(sortKey) : undefined}
      className={`text-xs font-display font-semibold py-2.5 px-2 sm:px-3 select-none ${align === 'right' ? 'text-right' : 'text-left'} ${sortable ? 'cursor-pointer' : title ? 'cursor-help' : ''} ${active ? 'text-accent' : 'text-fg-muted'} ${sortable ? 'hover:text-fg' : ''}`}
    >
      {children}{indicator}
    </th>
  );
}
