import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion, AnimatePresence } from 'framer-motion';
import { Activity, Layers } from 'lucide-react';
import EmptyState from '../../shared/components/feedback/EmptyState';
import LoadingState from '../../shared/components/feedback/LoadingState';
import ErrorState from '../../shared/components/feedback/ErrorState';
import Card from '../../shared/components/card';
import IndicatorCard from './components/IndicatorCard';
import YieldCurvePanel from './components/YieldCurvePanel';
import IndicatorHistoryModal from './components/IndicatorHistoryModal';
import { useMacroIndicators } from './hooks/useMacroIndicators';

const PROMINENT_ORDER = [
  'policyRate', 'tlrefRate', 'cpiIndex', 'ppiIndex',
  'depositTryTotal', 'depositUsdTotal', 'depositEurTotal',
];

function sortProminent(list) {
  const order = new Map(PROMINENT_ORDER.map((label, idx) => [label, idx]));
  return [...list].sort((a, b) => (order.get(a.label) ?? 99) - (order.get(b.label) ?? 99));
}

export default function MacroIndicatorsPanel() {
  const { t } = useTranslation();
  const [activeSubTab, setActiveSubTab] = useState('headline');
  const [openIndicator, setOpenIndicator] = useState(null);

  const { data: allIndicators = [], isLoading, isError, refetch } = useMacroIndicators();

  const prominent = useMemo(
    () => sortProminent(allIndicators.filter((i) => i.prominent)),
    [allIndicators]
  );

  if (isLoading) return <LoadingState message={t('marketOverview.macro.loading')} />;
  if (isError) return <ErrorState message={t('marketOverview.macro.error')} onRetry={refetch} />;
  if (allIndicators.length === 0) {
    return <EmptyState icon={<Activity className="h-6 w-6" />} message={t('marketOverview.macro.empty')} />;
  }

  return (
    <div className="space-y-4">
      <div className="inline-flex items-center gap-1 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 self-start">
        <SubTabButton
          active={activeSubTab === 'headline'}
          onClick={() => setActiveSubTab('headline')}
          icon={<Activity className="h-3.5 w-3.5" />}
        >
          {t('marketOverview.macro.headlineTab')}
        </SubTabButton>
        <SubTabButton
          active={activeSubTab === 'yieldCurve'}
          onClick={() => setActiveSubTab('yieldCurve')}
          icon={<Layers className="h-3.5 w-3.5" />}
        >
          {t('marketOverview.macro.yieldCurveTab')}
        </SubTabButton>
      </div>

      <AnimatePresence mode="wait" initial={false}>
        {activeSubTab === 'headline' && (
          <motion.div
            key="headline"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4"
          >
            {prominent.map((indicator) => (
              <IndicatorCard
                key={indicator.code}
                indicator={indicator}
                onOpen={setOpenIndicator}
              />
            ))}
            <YieldCurveCta onClick={() => setActiveSubTab('yieldCurve')} label={t('marketOverview.macro.yieldCurveCta')} />
          </motion.div>
        )}

        {activeSubTab === 'yieldCurve' && (
          <motion.div
            key="yieldCurve"
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
          >
            <YieldCurvePanel indicators={allIndicators} />
          </motion.div>
        )}
      </AnimatePresence>

      {openIndicator && (
        <IndicatorHistoryModal indicator={openIndicator} onClose={() => setOpenIndicator(null)} />
      )}
    </div>
  );
}

function SubTabButton({ active, onClick, icon, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`relative flex items-center gap-1.5 rounded-lg px-3 sm:px-4 py-2 text-xs font-medium transition-all border-none cursor-pointer ${
        active ? 'bg-accent/15 text-accent' : 'bg-transparent text-fg-muted hover:text-fg'
      }`}
    >
      {icon}
      {children}
    </button>
  );
}

function YieldCurveCta({ onClick, label }) {
  return (
    <Card
      as={motion.button}
      type="button"
      onClick={onClick}
      variant="outline"
      radius="xl"
      padding="md"
      interactive
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.98 }}
      className="w-full text-left flex flex-col items-center justify-center gap-2 border-dashed border-accent/30 cursor-pointer min-h-[140px]"
    >
      <Layers className="h-5 w-5 text-accent" />
      <span className="text-sm font-semibold text-accent">{label}</span>
    </Card>
  );
}
