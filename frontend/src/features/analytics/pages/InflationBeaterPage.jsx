import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useNavigate, useSearchParams } from 'react-router-dom';
import useSessionState from '../../../shared/hooks/useSessionState';
import LoadingState from '../../../shared/components/feedback/LoadingState';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import { useInflationBeaters, useAssetDisplayMeta } from '../hooks/useAnalytics';
import { useMacroIndicators } from '../../macro/hooks/useMacroIndicators';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';
import { PERIODS } from '../constants';
import { buildBackTarget } from '../lib/compareNav';
import {
  PAGE_SIZE,
  FIXED_TYPE_ORDER,
  BENCHMARK_CATEGORIES,
  MACRO_CATEGORY_TO_MARKET_TYPE,
  ANALYTICS_TO_MARKET_TYPE,
} from '../inflationBeaterConstants';
import BeaterPageHeader from '../components/beater/BeaterPageHeader';
import BeaterControls from '../components/beater/BeaterControls';
import BeaterHeroStats from '../components/beater/BeaterHeroStats';
import BeaterToolbar from '../components/beater/BeaterToolbar';
import BeaterTable from '../components/beater/BeaterTable';

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
      <BeaterPageHeader
        t={t}
        backTarget={backTarget}
        cameFrom={cameFrom}
        onBack={() => navigate(backTarget)}
      />

      <BeaterControls
        t={t}
        period={period}
        onPeriodChange={(id) => { setPeriod(id); setPage(0); }}
        benchmark={benchmark}
        onBenchmarkChange={(v) => { setBenchmark(v); setPage(0); }}
        benchmarkOptions={benchmarkOptions}
      />

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
      <BeaterHeroStats
        t={t}
        period={period}
        data={data}
        scopeBeating={scopeBeating}
        scopeTotal={scopeTotal}
        scopeLosing={scopeLosing}
        winRate={winRate}
        benchmarkLabel={benchmarkLabel}
      />

      <BeaterToolbar
        t={t}
        sortKey={sortKey}
        sortDir={sortDir}
        onToggleSort={onToggleSort}
        onReset={onReset}
        isDefaultView={isDefaultView}
        verdictFilter={verdictFilter}
        onVerdictChange={onVerdictChange}
        availableTypes={availableTypes}
        typeFilter={typeFilter}
        onTypeToggle={onTypeToggle}
        onClearTypes={onClearTypes}
        search={search}
        onSearchChange={onSearchChange}
        filteredCount={filteredEntries.length}
        totalCount={totalCount}
      />

      <BeaterTable
        t={t}
        sortKey={sortKey}
        sortDir={sortDir}
        verdictFilter={verdictFilter}
        typeFilter={typeFilter}
        search={search}
        page={page}
        pageEntries={pageEntries}
        onCompare={onCompare}
        metaFor={metaFor}
        nameFor={nameFor}
        safePage={safePage}
        totalPages={totalPages}
        onPageChange={onPageChange}
      />
    </>
  );
}
