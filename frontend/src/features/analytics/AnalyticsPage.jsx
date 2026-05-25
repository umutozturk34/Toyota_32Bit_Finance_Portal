import { useTranslation } from 'react-i18next';
import { AnimatePresence, motion } from 'framer-motion';
import { useSearchParams } from 'react-router-dom';
import { Activity, Trophy, GitCompare } from 'lucide-react';
import ScenarioComparePage from './pages/ScenarioComparePage';
import InflationBeaterPage from './pages/InflationBeaterPage';
import ComparePage from './pages/ComparePage';

const TABS = [
  { id: 'compare',   labelKey: 'tabCompare',   Icon: GitCompare },
  { id: 'scenario',  labelKey: 'tabScenario',  Icon: Activity },
  { id: 'beaters',   labelKey: 'tabBeaters',   Icon: Trophy },
];
const VALID_TABS = new Set(TABS.map((t) => t.id));

export default function AnalyticsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tabParam = params.get('tab');
  const active = VALID_TABS.has(tabParam) ? tabParam : 'compare';

  const setActive = (id) => {
    const next = new URLSearchParams(params);
    next.set('tab', id);
    setParams(next, { replace: true });
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-5"
    >
      <nav className="flex items-center gap-1 flex-wrap border-b border-border-default/40 pb-0.5 overflow-x-auto">
        {TABS.map(({ id, labelKey, Icon }) => {
          const isActive = active === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => setActive(id)}
              className={`relative flex items-center gap-2 px-3 sm:px-4 py-2.5 text-sm font-semibold cursor-pointer border-none transition-colors whitespace-nowrap ${
                isActive ? 'text-fg' : 'text-fg-muted hover:text-fg'
              }`}
            >
              <Icon className="h-4 w-4" />
              {t(`analytics.${labelKey}`, { defaultValue: id })}
              {isActive && (
                <motion.span
                  layoutId="analytics-tab-underline"
                  className="absolute left-2 right-2 -bottom-[1px] h-[2px] bg-accent rounded-full"
                />
              )}
            </button>
          );
        })}
      </nav>

      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={active}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }}
          transition={{ duration: 0.2 }}
        >
          {active === 'compare' && <ComparePage />}
          {active === 'scenario' && <ScenarioComparePage />}
          {active === 'beaters' && <InflationBeaterPage />}
        </motion.div>
      </AnimatePresence>
    </motion.div>
  );
}
