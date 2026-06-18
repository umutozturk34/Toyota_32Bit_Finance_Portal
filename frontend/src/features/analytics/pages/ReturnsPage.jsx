import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Medal } from 'lucide-react';
import useSessionState from '../../../shared/hooks/useSessionState';
import { useMoney } from '../../../shared/hooks/useMoney';
import { SkeletonList } from '../../../shared/components/feedback/Skeleton';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import { useAssetReturns, useAssetDisplayMeta } from '../hooks/useAnalytics';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';
import { RETURN_PERIODS } from '../constants';
import { moneyDigits } from '../utils';
import {
  PAGE_SIZE, FIXED_TYPE_ORDER, CCY_SYMBOL,
} from '../returnsConstants';
import ReturnsHeader from '../components/returns/ReturnsHeader';
import ReturnsPeriodBar from '../components/returns/ReturnsPeriodBar';
import ReturnsHeroStats from '../components/returns/ReturnsHeroStats';
import ReturnsControls from '../components/returns/ReturnsControls';
import ReturnsTable from '../components/returns/ReturnsTable';

export default function ReturnsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { currency: globalCcy } = useMoney();
  // Page-local currency: starts from the user's global default (USD/EUR; ORIGINAL or anything else → TRY) but
  // is changeable here WITHOUT touching the global preference.
  const [ccy, setCcy] = useSessionState('returns:ccy', ['USD', 'EUR'].includes(globalCcy) ? globalCcy : 'TRY');
  const [period, setPeriod] = useSessionState('returns:period', '1Y');
  const [search, setSearch] = useSessionState('returns:search', '');
  const [page, setPage] = useSessionState('returns:page', 0);
  const [typeFilterArr, setTypeFilterArr] = useSessionState('returns:type', []);
  const [riskFilterArr, setRiskFilterArr] = useSessionState('returns:risk', []);
  const [sortKey, setSortKey] = useSessionState('returns:sortField', 'return');
  const [sortDir, setSortDir] = useSessionState('returns:sortOrder', 'desc');
  const typeFilter = useMemo(() => new Set(typeFilterArr), [typeFilterArr]);
  const riskFilter = useMemo(() => new Set(riskFilterArr), [riskFilterArr]);

  const toggleType = (type) => {
    setPage(0);
    const next = new Set(typeFilter);
    if (next.has(type)) next.delete(type); else next.add(type);
    setTypeFilterArr(Array.from(next));
  };

  const toggleRisk = (lvl) => {
    setPage(0);
    const next = new Set(riskFilter);
    if (next.has(lvl)) next.delete(lvl); else next.add(lvl);
    setRiskFilterArr(Array.from(next));
  };

  const { data, isLoading, isError, refetch } = useAssetReturns();

  // Crypto (icons + proper names) and stocks (long company/index names) enriched from the market endpoint by
  // code; forex/commodity already resolve through instrumentDisplayName, funds stay on their code.
  const metaFor = useAssetDisplayMeta();
  const nameFor = (r) => metaFor(r.type, r.code)?.name || instrumentDisplayName(t, r.type, r.code, r.name);

  // Money formatted in the SELECTED page currency (₺ suffix for TRY; $/€ prefix otherwise).
  const money = (v) => {
    if (v == null) return '—';
    const n = Number(v);
    const num = n.toLocaleString('tr-TR', { maximumFractionDigits: moneyDigits(n) });
    return ccy === 'TRY' ? `${num} ₺` : `${CCY_SYMBOL[ccy] || ''}${num}`;
  };
  const moneyDelta = (v) => (v == null ? '—' : `${Number(v) > 0 ? '+' : ''}${money(v)}`);

  // One row per asset for the SELECTED window in the SELECTED currency. The per-currency figures are computed
  // server-side (the same calc on the FX-converted series) — TRY is the top level, USD/EUR their own block —
  // so this is a pure pick, no FX math here. A non-TRY window with no FX figure is dropped from that currency's
  // ranking rather than shown as a TRY number under a $/€ header (each currency is its own ranking).
  const rows = useMemo(() => {
    if (!data?.assets) return [];
    return data.assets
      .map((a) => {
        const pr = a.periods?.[period];
        if (!pr || pr.returnPct == null) return null;
        const f = ccy === 'TRY' ? pr : (ccy === 'USD' ? pr.usd : pr.eur);
        if (!f || f.returnPct == null) return null;
        const ret = Number(f.returnPct);
        const delta = ccy === 'TRY' ? f.returnTry : f.returnValue;
        const vol = f.volatility != null ? Number(f.volatility) : null;
        return {
          type: a.type,
          code: a.code,
          name: a.name,
          returnPct: ret,
          returnTry: delta != null ? Number(delta) : null,
          priceThen: f.priceThen != null ? Number(f.priceThen) : null,
          priceNow: f.priceNow != null ? Number(f.priceNow) : null,
          volatility: vol,
          // Risk-adjusted: return per unit of volatility — high when an asset delivers a lot of return for
          // little risk. Null when volatility is unknown, so those sink to the bottom of this ranking.
          riskAdjusted: vol && vol > 0 ? ret / vol : null,
          riskLevel: f.riskLevel || null,
        };
      })
      .filter(Boolean);
  }, [data, period, ccy]);

  const availableTypes = useMemo(() => {
    const present = new Set(rows.map((r) => r.type));
    return FIXED_TYPE_ORDER.filter((tp) => present.has(tp));
  }, [rows]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => {
      if (typeFilter.size > 0 && !typeFilter.has(r.type)) return false;
      if (riskFilter.size > 0 && (!r.riskLevel || !riskFilter.has(r.riskLevel))) return false;
      if (!q) return true;
      return r.code.toLowerCase().includes(q)
        || (r.name && r.name.toLowerCase().includes(q))
        || r.type.toLowerCase().includes(q);
    });
  }, [rows, search, typeFilter, riskFilter]);

  // Sorted view; the # column then numbers rows by their position in THIS order (1,2,3…), so sorting by any
  // column — or filtering by type — renumbers cleanly instead of carrying a stale global rank.
  const sorted = useMemo(() => {
    const arr = [...filtered];
    const num = (v) => (v == null ? -Infinity : Number(v));
    const cmpAsc = (a, b) => {
      if (sortKey === 'riskAdj') return num(a.riskAdjusted) - num(b.riskAdjusted);
      if (sortKey === 'price') return num(a.priceNow) - num(b.priceNow);
      if (sortKey === 'delta') return num(a.returnTry) - num(b.returnTry);
      if (sortKey === 'vol') return num(a.volatility) - num(b.volatility);
      return num(a.returnPct) - num(b.returnPct);
    };
    arr.sort((a, b) => (sortDir === 'asc' ? cmpAsc(a, b) : -cmpAsc(a, b)));
    return arr;
  }, [filtered, sortKey, sortDir]);

  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const pageRows = sorted.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  const best = filtered.length ? filtered.reduce((a, b) => (b.returnPct > a.returnPct ? b : a)) : null;
  const worst = filtered.length ? filtered.reduce((a, b) => (b.returnPct < a.returnPct ? b : a)) : null;
  const positiveCount = filtered.filter((r) => r.returnPct > 0).length;
  const negativeCount = filtered.filter((r) => r.returnPct < 0).length;
  const periodLabel = t(`analytics.${RETURN_PERIODS.find((p) => p.id === period)?.labelKey}`, { defaultValue: period });

  // Default view = Return ↓, no filters, no search. The reset chip appears only once the view drifts from it.
  const isDefaultView = sortKey === 'return' && sortDir === 'desc'
    && typeFilter.size === 0 && riskFilter.size === 0 && !search.trim();
  const resetView = () => {
    setSortKey('return');
    setSortDir('desc');
    setTypeFilterArr([]);
    setRiskFilterArr([]);
    setSearch('');
    setPage(0);
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="space-y-5"
    >
      <ReturnsHeader t={t} ccy={ccy} setCcy={setCcy} setPage={setPage} />

      <ReturnsPeriodBar t={t} period={period} setPeriod={setPeriod} setPage={setPage} />

      {isLoading && <SkeletonList rows={8} cols={4} className="mt-2" />}
      {isError && <ErrorState message={t('analytics.loadError', { defaultValue: 'Yüklenirken hata oluştu' })} onRetry={refetch} />}

      {data && (data.assets?.length ?? 0) === 0 && (
        <EmptyState
          icon={<Medal className="h-6 w-6" />}
          title={t('analytics.returns.title', { defaultValue: 'Varlık Getirileri' })}
          message={t('analytics.returns.preparing')}
        />
      )}

      {data && (data.assets?.length ?? 0) > 0 && (
        <>
          <ReturnsHeroStats
            t={t}
            best={best}
            worst={worst}
            positiveCount={positiveCount}
            negativeCount={negativeCount}
            nameFor={nameFor}
            periodLabel={periodLabel}
            filtered={filtered}
          />

          <ReturnsControls
            t={t}
            sortKey={sortKey}
            setSortKey={setSortKey}
            sortDir={sortDir}
            setSortDir={setSortDir}
            setPage={setPage}
            resetView={resetView}
            isDefaultView={isDefaultView}
            availableTypes={availableTypes}
            typeFilter={typeFilter}
            toggleType={toggleType}
            setTypeFilterArr={setTypeFilterArr}
            riskFilter={riskFilter}
            toggleRisk={toggleRisk}
            setRiskFilterArr={setRiskFilterArr}
            search={search}
            setSearch={setSearch}
            filtered={filtered}
            rows={rows}
          />

          <ReturnsTable
            t={t}
            sortKey={sortKey}
            sortDir={sortDir}
            ccy={ccy}
            period={period}
            typeFilter={typeFilter}
            riskFilter={riskFilter}
            search={search}
            page={page}
            pageRows={pageRows}
            safePage={safePage}
            totalPages={totalPages}
            setPage={setPage}
            metaFor={metaFor}
            nameFor={nameFor}
            money={money}
            moneyDelta={moneyDelta}
            navigate={navigate}
          />
        </>
      )}
    </motion.div>
  );
}
