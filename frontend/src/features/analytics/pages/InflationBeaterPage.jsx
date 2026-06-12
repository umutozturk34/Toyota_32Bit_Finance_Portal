import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useNavigate, useSearchParams } from 'react-router-dom';
import useSessionState from '../../../shared/hooks/useSessionState';
import { TrendingUp, TrendingDown, Trophy, Search, ChevronLeft, ChevronRight, GitCompare, ArrowUp, ArrowDown, RotateCcw, ArrowLeft } from 'lucide-react';
import Card from '../../../shared/components/card';
import LoadingState from '../../../shared/components/feedback/LoadingState';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import { useInflationBeaters, useAssetDisplayMeta } from '../hooks/useAnalytics';
import { useMacroIndicators } from '../../macro/hooks/useMacroIndicators';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';
import BenchmarkPicker from '../components/BenchmarkPicker';
import HeroStat from '../components/BeaterHeroStat';
import Th from '../components/BeaterTh';
import { PERIODS } from '../constants';
import { formatPercent } from '../utils';
import { buildBackTarget } from '../lib/compareNav';
import {
  PAGE_SIZE,
  FIXED_TYPE_ORDER,
  SORT_OPTIONS,
  BENCHMARK_CATEGORIES,
  MACRO_CATEGORY_TO_MARKET_TYPE,
  ANALYTICS_TO_MARKET_TYPE,
  TYPE_BADGE,
} from '../inflationBeaterConstants';

export default function InflationBeaterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  // When reached from a widget/page (e.g. the overview Beaters widget passes ?from=overview), offer a
  // one-click way back to where the user came from — mirrors ComparePage. Absent otherwise (tab/deep-link).
  const cameFrom = params.get('from');
  const backTarget = buildBackTarget(cameFrom, params.get('fromType'), params.get('fromCode'));
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
    // eslint-disable-next-line react-hooks/exhaustive-deps -- params/setParams omitted on purpose: setParams is unstable in react-router v7, adding it self-triggers an infinite URL-write loop
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

  const { data, isLoading, isError, refetch } = useInflationBeaters(period, benchmark);
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
    // Pin Compare to the exact CPI-publication-anchored window Beater computed against
    // (data.startDate..data.endDate) so the % numbers in CompareInfoBar match the Beater
    // table row-for-row. forwardFillTo(bounds.to) keeps sparse macro lines extended to the
    // right edge of that window — no flatline gap after the CPI anchor date.
    if (data?.startDate) next.set('start', data.startDate);
    if (data?.endDate) next.set('end', data.endDate);
    if (data?.comparisonCurrency) next.set('currency', data.comparisonCurrency);
    // Carry the Beater's authoritative (backend-computed, cached) nominal returns as code:pct pairs so
    // CompareInfoBar prints the SAME numbers as the row clicked. The frontend re-compound anchors the
    // lead-in slightly differently (first in-window observation vs the prior one), drifting the % ~0.5pt
    // off the table; pinning the cached value keeps the two screens agreeing to the decimal.
    const nominals = [];
    if (entry.nominalReturnPct != null) nominals.push(`${entry.code}:${entry.nominalReturnPct}`);
    if (data?.benchmarkCode && data?.benchmarkReturnPct != null) {
      nominals.push(`${data.benchmarkCode}:${data.benchmarkReturnPct}`);
    }
    if (nominals.length > 0) next.set('nominals', nominals.join(','));
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
        {backTarget && (
          <motion.button
            type="button"
            onClick={() => navigate(backTarget)}
            whileHover={{ x: -2 }}
            whileTap={{ scale: 0.97 }}
            transition={{ type: 'spring', stiffness: 400, damping: 28 }}
            className="group inline-flex items-center gap-2 mb-4 rounded-full border border-border-default/80 bg-bg-elevated/80 backdrop-blur-md pl-1.5 pr-4 py-1.5 text-fg-muted hover:text-accent hover:border-accent/50 hover:bg-accent/10 transition-colors cursor-pointer"
            style={{ boxShadow: '0 2px 12px -4px rgba(0,0,0,0.3)' }}
          >
            <span className="flex items-center justify-center w-7 h-7 rounded-full bg-bg-base/70 border border-border-default/60 group-hover:bg-accent/15 group-hover:border-accent/50 group-hover:shadow-[0_0_10px_-2px_rgba(99,102,241,0.5)] transition-all">
              <ArrowLeft className="h-3.5 w-3.5 transition-transform group-hover:-translate-x-0.5" />
            </span>
            <span className="font-display text-sm font-semibold tracking-tight">
              {t(`analytics.backTo.${cameFrom}`, { defaultValue: 'Geri dön' })}
            </span>
          </motion.button>
        )}
        <div className="flex items-center gap-2.5">
          <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-accent/12 text-accent shrink-0">
            <Trophy className="h-5 w-5" />
          </span>
          <h1 className="font-display text-2xl sm:text-3xl font-bold text-fg tracking-tight leading-none">
            {t('analytics.beaterTitle', { defaultValue: 'Benchmark Yenenler' })}
          </h1>
        </div>
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
          onReset={() => {
            setSortKey('rank');
            setSortDir('asc');
            setVerdictFilter('all');
            setTypeFilter(new Set());
            setSearch('');
            setPage(0);
          }}
          onCompare={compareWithBenchmark}
        />
      )}
    </motion.div>
  );
}

function Results({ data, period, t, search, onSearchChange, page, onPageChange, onCompare,
                   verdictFilter, onVerdictChange, typeFilter, onTypeToggle, onClearTypes,
                   sortKey, sortDir, onToggleSort, onReset }) {
  const indexedEntries = useMemo(() => data.entries || [], [data.entries]);
  const totalCount = indexedEntries.length;
  // Crypto logos + proper names (e.g. "Bitcoin" instead of the bare code) enriched from the market catalogue,
  // shared with the Returns page; forex/commodity resolve through instrumentDisplayName, funds stay on code.
  const metaFor = useAssetDisplayMeta();
  const nameFor = (entry) => metaFor(entry.type, entry.code)?.name
    || instrumentDisplayName(t, entry.type, entry.code, entry.name);
  const isDefaultView = sortKey === 'rank' && sortDir === 'asc'
    && verdictFilter === 'all' && typeFilter.size === 0 && !search.trim();
  // Hero stats follow the active type/search filter (NOT the verdict view-toggle, which would make the
  // beating/losing counts trivial), so narrowing the population live-updates the numbers above the table.
  const statScope = useMemo(() => {
    const q = search.trim().toLowerCase();
    return indexedEntries.filter((e) => {
      if (typeFilter.size > 0 && !typeFilter.has(e.type)) return false;
      if (!q) return true;
      return e.code.toLowerCase().includes(q)
        || (e.name && e.name.toLowerCase().includes(q))
        || e.type.toLowerCase().includes(q);
    });
  }, [indexedEntries, search, typeFilter]);
  const scopeTotal = statScope.length;
  const scopeBeating = statScope.filter((e) => e.beatsBenchmark).length;
  const scopeLosing = scopeTotal - scopeBeating;
  const winRate = scopeTotal > 0 ? Math.round((scopeBeating / scopeTotal) * 100) : 0;

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

  // Rank recomputed WITHIN the filtered subset (filteredEntries keeps the backend excess-desc order), so
  // narrowing to one asset type renumbers 1,2,3… instead of keeping the global position (1,3,5…).
  const rankedFiltered = useMemo(
    () => filteredEntries.map((e, idx) => ({ ...e, _displayRank: idx + 1 })),
    [filteredEntries]
  );

  const sortedEntries = useMemo(() => {
    const arr = [...rankedFiltered];
    const num = (v) => (v == null ? -Infinity : Number(v));
    const cmpAsc = (a, b) => {
      if (sortKey === 'nominal') return num(a.nominalReturnPct) - num(b.nominalReturnPct);
      if (sortKey === 'excess') return num(a.excessReturnPct) - num(b.excessReturnPct);
      return a._displayRank - b._displayRank;
    };
    arr.sort((a, b) => (sortDir === 'asc' ? cmpAsc(a, b) : -cmpAsc(a, b)));
    return arr;
  }, [rankedFiltered, sortKey, sortDir]);

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
          value={`${scopeBeating}/${scopeTotal}`}
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
          value={scopeLosing > 0
            ? `${scopeLosing}`
            : <span className="text-sm text-fg-muted">{t('analytics.noUnderperformers', { defaultValue: 'Altta kalan yok' })}</span>}
          sub={scopeLosing > 0
            ? t('analytics.realLoss', { defaultValue: 'Excess return < 0' })
            : t('analytics.allBeatSub', { defaultValue: 'Hepsi göstergeyi yendi' })}
          accent={scopeLosing > 0 ? '#ef4444' : '#6b7280'}
        />
      </div>

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
          {filteredEntries.length} / {totalCount}
        </span>
      </div>

      <Card variant="elevated" radius="xl" padding="none" backdropBlur className="overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-xs sm:text-sm min-w-[560px]">
            <thead className="bg-bg-elevated/40">
              <tr>
                <Th sortKey="rank" activeSort={sortKey} dir={sortDir}>#</Th>
                <Th>
                  {t('analytics.instrument', { defaultValue: 'Enstrüman' })}
                </Th>
                <Th align="right" sortKey="nominal" activeSort={sortKey} dir={sortDir} title={t('analytics.nominalReturnTooltip', { defaultValue: 'Mutlak yüzde değişim' })}>
                  {t('analytics.nominalReturn', { defaultValue: 'Nominal' })}
                </Th>
                <Th align="right" sortKey="excess" activeSort={sortKey} dir={sortDir} title={t('analytics.excessReturnTooltip', { defaultValue: 'Nominal − Gösterge' })}>
                  {t('analytics.excessReturn', { defaultValue: 'Gösterge Üzeri' })}
                </Th>
                <Th align="right">{t('analytics.verdict', { defaultValue: 'Sonuç' })}</Th>
              </tr>
            </thead>
            <tbody>
              {pageEntries.map((entry) => (
                <tr
                  key={`${entry.type}|${entry.code}`}
                  onClick={() => onCompare?.(entry)}
                  className="group border-t border-border-default/40 hover:bg-bg-elevated/40 transition-colors cursor-pointer"
                  title={t('analytics.openInCompare', { defaultValue: 'Compare’de aç' })}
                >
                  <td className="py-3 px-2 sm:px-3 font-mono text-xs tabular-nums">
                    <span className={entry._displayRank <= 3 ? 'text-warning font-bold' : 'text-fg-muted'}>
                      {entry._displayRank}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3">
                    {(() => {
                      const badge = TYPE_BADGE[entry.type] || { label: entry.type, color: '#6366f1' };
                      // Crypto shows its CoinGecko logo (same as the Returns page); everything else keeps the
                      // colored initials avatar so each asset still has an at-a-glance visual identity.
                      const icon = entry.type === 'CRYPTO' ? metaFor(entry.type, entry.code)?.image : null;
                      const initials = (entry.code || '').replace('.IS', '').slice(0, 2).toUpperCase();
                      return (
                        <>
                          <div className="flex items-center gap-2 flex-wrap">
                            {icon ? (
                              <img
                                src={icon}
                                alt=""
                                loading="lazy"
                                className="w-6 h-6 rounded-full ring-1 ring-border-default shrink-0"
                              />
                            ) : (
                              <span
                                className="w-6 h-6 rounded-full shrink-0 flex items-center justify-center text-[9px] font-bold text-white shadow-sm"
                                style={{ backgroundColor: badge.color }}
                                aria-hidden
                              >
                                {initials}
                              </span>
                            )}
                            <span className="text-fg font-semibold">{nameFor(entry)}</span>
                            <span
                              className="inline-flex items-center text-[10px] font-mono font-semibold tracking-[0.04em] rounded px-1.5 py-0.5"
                              style={{ background: `${badge.color}1f`, color: badge.color, boxShadow: `inset 0 0 0 1px ${badge.color}40` }}
                            >
                              {t(`assets.labels.${entry.type}`, { defaultValue: badge.label })}
                            </span>
                            <GitCompare className="h-3 w-3 text-fg-subtle opacity-0 group-hover:opacity-100 transition-opacity" />
                          </div>
                          <div className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle mt-0.5">
                            {entry.code}
                          </div>
                        </>
                      );
                    })()}
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right font-mono tabular-nums">
                    <span className={Number(entry.nominalReturnPct) >= 0 ? 'text-success' : 'text-danger'}>
                      {formatPercent(entry.nominalReturnPct)}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right font-mono font-bold tabular-nums">
                    <span className={Number(entry.excessReturnPct) >= 0 ? 'text-success' : 'text-danger'}>
                      {formatPercent(entry.excessReturnPct)}
                    </span>
                  </td>
                  <td className="py-3 px-2 sm:px-3 text-right">
                    {entry.beatsBenchmark ? (
                      <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.14em] rounded-md px-2 py-0.5 bg-success/15 text-success">
                        <Trophy className="h-3 w-3" />
                        {t('analytics.beats', { defaultValue: 'Yendi' })}
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.14em] rounded-md px-2 py-0.5 bg-danger/10 text-danger">
                        <TrendingDown className="h-3 w-3" />
                        {t('analytics.lost', { defaultValue: 'Kaybetti' })}
                      </span>
                    )}
                  </td>
                </tr>
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
