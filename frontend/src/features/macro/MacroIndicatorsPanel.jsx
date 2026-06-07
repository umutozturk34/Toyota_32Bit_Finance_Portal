import { useCallback, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Activity, Banknote, Flame, Layers, LayoutGrid, Star, Search, X } from 'lucide-react';
import EmptyState from '../../shared/components/feedback/EmptyState';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import IndicatorCard from './components/IndicatorCard';
import DepositMatrix from './components/DepositMatrix';
import YieldCurvePanel from './components/YieldCurvePanel';
import IndicatorHistoryModal from './components/IndicatorHistoryModal';
import { useMacroIndicators } from './hooks/useMacroIndicators';
import { DEPOSIT_CURRENCIES, PROMINENT_ORDER } from './constants';
import { themeFor } from './utils';

const TABS = [
  { id: 'all',       icon: LayoutGrid, labelKey: 'tabAll',         category: null },
  { id: 'rates',     icon: Activity, labelKey: 'categoryRates',    category: 'RATES' },
  { id: 'inflation', icon: Flame,    labelKey: 'categoryInflation', category: 'INFLATION' },
  { id: 'deposit',   icon: Banknote, labelKey: 'categoryDeposit',  category: 'DEPOSIT' },
];

function sortByProminentOrder(list) {
  const order = new Map(PROMINENT_ORDER.map((label, idx) => [label, idx]));
  return [...list].sort((a, b) => (order.get(a.label) ?? 99) - (order.get(b.label) ?? 99));
}

export default function MacroIndicatorsPanel() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('all');
  const [currencyFilter, setCurrencyFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchParams, setSearchParams] = useSearchParams();

  const { data: allIndicators = [], isLoading, isError, refetch } = useMacroIndicators();

  const focusCode = searchParams.get('indicator');
  const openIndicator = useMemo(
    () => (focusCode ? allIndicators.find((i) => i.code === focusCode) ?? null : null),
    [focusCode, allIndicators],
  );

  const openWithIndicator = useCallback((indicator) => {
    const next = new URLSearchParams(searchParams);
    next.set('indicator', indicator.code);
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const handleCloseModal = useCallback(() => {
    if (!searchParams.has('indicator')) return;
    const next = new URLSearchParams(searchParams);
    next.delete('indicator');
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  const counts = useMemo(() => ({
    all: allIndicators.length,
    rates: allIndicators.filter((i) => i.category === 'RATES').length,
    inflation: allIndicators.filter((i) => i.category === 'INFLATION').length,
    deposit: allIndicators.filter((i) => i.category === 'DEPOSIT').length,
  }), [allIndicators]);

  const visible = useMemo(() => {
    const activeDef = TABS.find((tab) => tab.id === activeTab) || TABS[0];
    let list = activeDef.category ? allIndicators.filter((i) => i.category === activeDef.category) : allIndicators;
    if (activeTab === 'deposit' && currencyFilter !== 'ALL') {
      list = list.filter((i) => i.currency === currencyFilter);
    }
    const q = searchQuery.trim().toLowerCase();
    if (q) {
      list = list.filter((i) => {
        const friendlyLabel = i.label
          ? t(`marketOverview.macro.${i.label}`, { defaultValue: i.label })
          : (i.name || '');
        return friendlyLabel.toLowerCase().includes(q)
          || (i.name || '').toLowerCase().includes(q)
          || (i.code || '').toLowerCase().includes(q)
          || (i.label || '').toLowerCase().includes(q);
      });
    }
    return list;
  }, [activeTab, currencyFilter, allIndicators, searchQuery, t]);

  const prominent = useMemo(
    () => sortByProminentOrder(allIndicators.filter((i) => i.prominent)),
    [allIndicators]
  );

  if (isLoading) return <LoadingState message={t('marketOverview.macro.loading')} />;
  if (isError) return <ErrorState message={t('marketOverview.macro.error')} onRetry={refetch} />;
  if (allIndicators.length === 0) {
    return <EmptyState icon={<Activity className="h-6 w-6" />} message={t('marketOverview.macro.empty')} />;
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="space-y-6"
    >
      <header className="flex items-end justify-between gap-3 flex-wrap pb-2 border-b border-border-default/40">
        <div>
          <h2 className="font-display text-xl sm:text-2xl font-bold text-fg leading-none tracking-tight">
            {t('marketOverview.macro.heading', { defaultValue: 'Macro Indicators' })}
          </h2>
          <p className="mt-1.5 text-[11px] font-mono text-fg-muted uppercase tracking-[0.18em]">
            {t('marketOverview.macro.subheading', {
              defaultValue: 'TCMB EVDS · weekly + monthly + daily',
            })}
          </p>
        </div>
        <div className="font-mono text-[10px] tabular-nums text-fg-subtle uppercase tracking-[0.14em]">
          {counts.all} series
        </div>
      </header>

      <div className="flex items-center gap-2 flex-wrap">
        <nav className="flex items-center gap-1 overflow-x-auto sm:flex-wrap sm:overflow-x-visible scrollbar-thin flex-1 min-w-0">
          {TABS.map((tab) => {
          const Icon = tab.icon;
          const active = activeTab === tab.id;
          const theme = tab.category ? themeFor(tab.category) : null;
          const count = counts[tab.id];
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => { setActiveTab(tab.id); setCurrencyFilter('ALL'); }}
              className={`group/tab relative flex items-center gap-2 rounded-xl px-3.5 py-2 text-xs font-semibold transition-all border-none cursor-pointer ${
                active ? 'text-fg' : 'text-fg-muted hover:text-fg'
              }`}
              style={active && theme ? { background: theme.soft, boxShadow: `inset 0 0 0 1px ${theme.accent}40` } : active ? { background: 'rgba(99,102,241,0.10)', boxShadow: 'inset 0 0 0 1px rgba(99,102,241,0.40)' } : {}}
            >
              <Icon className="h-3.5 w-3.5" />
              <span>{t(`marketOverview.macro.${tab.labelKey}`, { defaultValue: tab.id })}</span>
              <span className="font-mono text-[10px] tabular-nums opacity-70">{String(count).padStart(2, '0')}</span>
            </button>
          );
        })}
        </nav>
        <div className="relative w-full sm:w-56">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-muted pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('marketOverview.macro.searchPlaceholder', { defaultValue: 'Gösterge ara…' })}
            className="w-full pl-8 pr-7 py-1.5 rounded-lg bg-bg-elevated border border-border-default text-xs text-fg placeholder:text-fg-muted focus:outline-none focus:border-accent/60"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              className="absolute right-1.5 top-1/2 -translate-y-1/2 text-fg-muted hover:text-fg p-0.5 bg-transparent border-none cursor-pointer"
            >
              <X className="h-3 w-3" />
            </button>
          )}
        </div>
      </div>

      {activeTab === 'deposit' && (
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] font-mono uppercase tracking-[0.14em] text-fg-muted mr-1">
            {t('marketOverview.macro.currency', { defaultValue: 'Currency' })}
          </span>
          {['ALL', ...DEPOSIT_CURRENCIES].map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setCurrencyFilter(c)}
              className={`text-[11px] font-mono font-semibold rounded-md px-2.5 py-1 transition-colors border-none cursor-pointer ${
                currencyFilter === c ? 'bg-bg-elevated text-fg' : 'text-fg-muted hover:text-fg'
              }`}
            >
              {c === 'ALL' ? t('marketOverview.macro.tabAll', { defaultValue: 'All' }) : c}
            </button>
          ))}
        </div>
      )}

      <AnimatePresence mode="wait" initial={false}>
        {activeTab === 'all' && (
          <motion.div
            key="all"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.2 }}
            className="space-y-6"
          >
            <section>
              <SectionTitle icon={Star} text={t('marketOverview.macro.headline', { defaultValue: 'Headline' })} hint={`${prominent.length}`} />
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                {prominent.map((indicator) => (
                  <IndicatorCard key={indicator.code} indicator={indicator} onOpen={openWithIndicator} />
                ))}
              </div>
            </section>

            <section>
              <SectionTitle icon={Banknote} text={t('marketOverview.macro.depositMatrixTitle', { defaultValue: 'Deposit Rate Matrix' })} />
              <DepositMatrix indicators={allIndicators} onOpen={openWithIndicator} />
            </section>

            <section>
              <SectionTitle icon={Layers} text={t('marketOverview.macro.yieldCurveTab', { defaultValue: 'Yield Curve' })} />
              <YieldCurvePanel indicators={allIndicators} />
            </section>
          </motion.div>
        )}

        {activeTab !== 'all' && (
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.2 }}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3"
          >
            {visible.map((indicator) => (
              <IndicatorCard key={indicator.code} indicator={indicator} onOpen={openWithIndicator} />
            ))}
          </motion.div>
        )}
      </AnimatePresence>

      {openIndicator && (
        <IndicatorHistoryModal indicator={openIndicator} onClose={handleCloseModal} />
      )}
    </motion.div>
  );
}

function SectionTitle({ icon: Icon, text, hint }) {
  return (
    <div className="flex items-center gap-2 mb-3">
      <Icon className="h-4 w-4 text-accent" />
      <h3 className="font-display text-xs font-bold uppercase tracking-[0.18em] text-fg">{text}</h3>
      {hint && <span className="font-mono text-[10px] tabular-nums text-fg-subtle">{hint}</span>}
    </div>
  );
}
