import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { EASE } from '../../../shared/utils/animations';
import {
  TrendingUp, TrendingDown, Medal, Search, ChevronLeft, ChevronRight, ArrowUp, ArrowDown, ArrowUpRight, ShieldAlert, RotateCcw,
} from 'lucide-react';
import useSessionState from '../../../shared/hooks/useSessionState';
import { useMoney } from '../../../shared/hooks/useMoney';
import Card from '../../../shared/components/card';
import LoadingState from '../../../shared/components/feedback/LoadingState';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import { useAssetReturns, useAssetDisplayMeta } from '../hooks/useAnalytics';
import { instrumentDisplayName } from '../../../shared/utils/instrumentLabel';
import { RETURN_PERIODS } from '../constants';
import { formatPercent, moneyDigits } from '../utils';
import { assetRoute } from '../../watch/lib/watchConstants';
import {
  PAGE_SIZE, ANALYTICS_TO_MARKET_TYPE, FIXED_TYPE_ORDER, TYPE_BADGE, RISK_STYLE, SORT_OPTIONS, CCY_SYMBOL,
} from '../returnsConstants';
import HeroStat from '../components/HeroStat';
import Th from '../components/ReturnsTh';

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
      <header className="pb-3 border-b border-border-default/40">
        <h1 className="font-display text-2xl font-bold text-fg tracking-tight leading-none">
          {t('analytics.returns.title', { defaultValue: 'Varlık Getirileri' })}
        </h1>
        <p className="mt-2 text-sm text-fg-muted max-w-2xl">
          {t('analytics.returns.subtitle')}
        </p>
        <div className="flex flex-wrap items-center gap-2 mt-2">
          <span className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle">
            {t('analytics.returns.currencyLabel', { defaultValue: 'Para birimi' })}
          </span>
          {['TRY', 'USD', 'EUR'].map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => { setCcy(c); setPage(0); }}
              className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 cursor-pointer border-none transition-colors ${
                ccy === c ? 'bg-accent/15 text-accent shadow-[inset_0_0_0_1px_rgba(99,102,241,0.4)]' : 'text-fg-muted hover:text-fg'
              }`}
            >
              {CCY_SYMBOL[c]} {c}
            </button>
          ))}
        </div>
        <p className="mt-2 text-[11px] text-fg-subtle leading-snug max-w-3xl">
          {t('analytics.returns.currencyHint', { defaultValue: 'Getiriler seçtiğin para biriminde; her tarih kendi günündeki kurla çevrilir. Varsayılan genel para birimi tercihinden gelir, burada serbestçe değiştirebilirsin.' })} {t('analytics.returns.usageHint')}
        </p>
      </header>

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

      {isLoading && <LoadingState message={t('analytics.loading', { defaultValue: 'Hesaplanıyor...' })} />}
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
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <HeroStat
              icon={<TrendingUp className="h-4 w-4" />}
              label={t('analytics.returns.topGainer')}
              value={positiveCount > 0
                ? <span className="text-success">{formatPercent(best.returnPct)}</span>
                : <span className="text-sm text-fg-muted">{t('analytics.returns.noGainers', { defaultValue: 'Bu dönemde kazanan yok' })}</span>}
              sub={positiveCount > 0 ? nameFor(best) : periodLabel}
              accent={positiveCount > 0 ? '#10b981' : '#6b7280'}
            />
            <HeroStat
              icon={<TrendingDown className="h-4 w-4" />}
              label={t('analytics.returns.topLoser')}
              value={negativeCount > 0
                ? <span className="text-danger">{formatPercent(worst.returnPct)}</span>
                : <span className="text-sm text-fg-muted">{t('analytics.returns.noLosers', { defaultValue: 'Bu dönemde kaybeden yok' })}</span>}
              sub={negativeCount > 0 ? nameFor(worst) : periodLabel}
              accent={negativeCount > 0 ? '#ef4444' : '#6b7280'}
            />
            <HeroStat
              icon={<Medal className="h-4 w-4" />}
              label={t('analytics.returns.profitLoss', { defaultValue: 'Kâr / Zarar' })}
              value={(
                <span className="inline-flex items-center gap-3 text-fg">
                  <span className="inline-flex items-center gap-1.5">
                    <TrendingUp className="h-5 w-5 text-success" />{positiveCount}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <TrendingDown className="h-5 w-5 text-danger" />{negativeCount}
                  </span>
                </span>
              )}
              sub={`${filtered.length} ${t('analytics.returns.assetCount')} · ${periodLabel}`}
              accent="#5E6AD2"
            />
          </div>

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

          <Card variant="elevated" radius="xl" padding="none" backdropBlur className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-xs sm:text-sm min-w-[640px]">
                <thead className="bg-bg-elevated/40">
                  <tr>
                    <Th>#</Th>
                    <Th>{t('analytics.instrument', { defaultValue: 'Enstrüman' })}</Th>
                    <Th align="right" active={sortKey === 'price'} dir={sortDir}>{t('analytics.returns.price', { defaultValue: 'Fiyat' })}</Th>
                    <Th align="right" active={sortKey === 'return'} dir={sortDir}>{t('analytics.returns.return', { defaultValue: 'Getiri' })}</Th>
                    <Th align="right" active={sortKey === 'delta'} dir={sortDir}>{t('analytics.returns.deltaTry', { defaultValue: '₺ Değişim' })}</Th>
                    <Th align="right" active={sortKey === 'vol'} dir={sortDir} title={t('analytics.returns.riskInfo')}>
                      {t('analytics.returns.risk', { defaultValue: 'Risk' })}
                    </Th>
                  </tr>
                </thead>
                <motion.tbody
                  key={`${sortKey}-${sortDir}-${ccy}-${period}-${[...typeFilter].sort().join(',')}-${[...riskFilter].sort().join(',')}-${search.trim()}-${page}`}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.26, ease: EASE.standard }}
                >
                  {pageRows.map((r, idx) => {
                    const badge = TYPE_BADGE[r.type] || { label: r.type, color: '#6366f1' };
                    const risk = r.riskLevel ? RISK_STYLE[r.riskLevel] : null;
                    const rank = safePage * PAGE_SIZE + idx + 1;
                    const detail = assetRoute(ANALYTICS_TO_MARKET_TYPE[r.type], r.code);
                    const icon = r.type === 'CRYPTO' ? metaFor(r.type, r.code)?.image : null;
                    return (
                      <tr
                        key={`${r.type}|${r.code}`}
                        onClick={() => { if (detail) navigate(detail); }}
                        className="group border-t border-border-default/40 hover:bg-bg-elevated/40 transition-colors cursor-pointer"
                        title={t('analytics.returns.openDetail', { defaultValue: 'Detayı aç' })}
                      >
                        <td className="py-3 px-2 sm:px-3 font-mono text-xs tabular-nums">
                          <span className={rank <= 3 ? 'text-warning font-bold' : 'text-fg-muted'}>{rank}</span>
                        </td>
                        <td className="py-3 px-2 sm:px-3">
                          <div className="flex items-center gap-2 flex-wrap">
                            {icon && <img src={icon} alt="" loading="lazy" className="w-5 h-5 rounded-full ring-1 ring-border-default shrink-0" />}
                            <span className="text-fg font-semibold">{nameFor(r)}</span>
                            <span
                              className="inline-flex items-center text-[10px] font-mono font-semibold tracking-[0.04em] rounded px-1.5 py-0.5"
                              style={{ background: `${badge.color}1f`, color: badge.color, boxShadow: `inset 0 0 0 1px ${badge.color}40` }}
                            >
                              {t(`assets.labels.${r.type}`, { defaultValue: badge.label })}
                            </span>
                            <ArrowUpRight className="h-3 w-3 text-fg-subtle opacity-0 group-hover:opacity-100 transition-opacity" />
                          </div>
                          <div className="text-[10px] font-mono uppercase tracking-[0.12em] text-fg-subtle mt-0.5">{r.code}</div>
                        </td>
                        <td className="py-3 px-2 sm:px-3 text-right font-mono text-[11px] tabular-nums whitespace-nowrap">
                          <span className="text-fg-subtle">{money(r.priceThen)}</span>
                          <span className="text-fg-subtle mx-1">→</span>
                          <span className="text-fg">{money(r.priceNow)}</span>
                        </td>
                        <td className="py-3 px-2 sm:px-3 text-right font-mono font-bold tabular-nums">
                          <span className={r.returnPct >= 0 ? 'text-success' : 'text-danger'}>
                            {formatPercent(r.returnPct)}
                          </span>
                        </td>
                        <td className="py-3 px-2 sm:px-3 text-right font-mono tabular-nums whitespace-nowrap">
                          <span className={r.returnTry == null ? 'text-fg-subtle' : r.returnTry >= 0 ? 'text-success' : 'text-danger'}>
                            {moneyDelta(r.returnTry)}
                          </span>
                        </td>
                        <td className="py-3 px-2 sm:px-3 text-right">
                          {risk ? (
                            <div className="inline-flex flex-col items-end gap-0.5">
                              <span className={`inline-flex items-center gap-1 text-[10px] font-mono uppercase tracking-[0.12em] rounded-md px-2 py-0.5 border ${risk.badge}`}>
                                <ShieldAlert className="h-3 w-3" />
                                {t(risk.key)}
                              </span>
                              {r.volatility != null && (
                                <span className="text-[10px] font-mono text-fg-muted tabular-nums" title={t('analytics.returns.riskInfo')}>
                                  σ {r.volatility.toLocaleString('tr-TR', { maximumFractionDigits: 1 })}%
                                </span>
                              )}
                            </div>
                          ) : (
                            <span className="text-fg-subtle font-mono text-xs">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                  {pageRows.length === 0 && (
                    <tr>
                      <td colSpan={6} className="py-6 text-center text-xs text-fg-muted font-mono italic">
                        {t('analytics.noMatch', { defaultValue: 'Eşleşme yok' })}
                      </td>
                    </tr>
                  )}
                </motion.tbody>
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
                    onClick={() => setPage(Math.max(0, safePage - 1))}
                    disabled={safePage === 0}
                    className="h-7 w-7 flex items-center justify-center rounded-md text-fg-muted hover:text-fg hover:bg-bg-elevated transition-colors border-none cursor-pointer bg-transparent disabled:opacity-30 disabled:cursor-not-allowed"
                  >
                    <ChevronLeft className="h-3.5 w-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setPage(Math.min(totalPages - 1, safePage + 1))}
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
      )}
    </motion.div>
  );
}
