import { motion } from 'framer-motion';
import { useTranslation } from 'react-i18next';
import { SPRING } from '../../utils/animations';

export default function FilterTabs({ items, activeId, onSelect, allLabel, allCount, showAll = true, layoutId = 'filter-tab' }) {
  const { t } = useTranslation();
  const allLabelText = allLabel ?? t('common.all');
  const allItems = showAll
    ? [{ type: 'ALL', label: allLabelText, count: allCount }, ...items]
    : items;

  return (
    <div className="flex max-w-full gap-1 overflow-x-auto rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 sm:inline-flex sm:max-w-none">
      {allItems.map(({ type, count, label }) => {
        const active = activeId === type;
        return (
          <button
            key={type}
            onClick={() => onSelect(type)}
            className="relative shrink-0 rounded-xl px-3 sm:px-3.5 py-2 sm:py-1.5 min-h-9 sm:min-h-0 border-none cursor-pointer bg-transparent transition-colors"
          >
            {active && (
              <motion.span
                layoutId={layoutId}
                className="absolute inset-0 rounded-xl bg-accent/12 shadow-sm shadow-accent/10"
                style={{ borderRadius: 12 }}
                transition={SPRING.tab}
              />
            )}
            <span className="relative z-10 flex items-center gap-1.5">
              <span className={`text-xs font-semibold tracking-tight transition-colors duration-200 ${active ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                {label || type}
              </span>
              {count != null && (
                <span className={`text-[10px] font-mono tabular-nums transition-colors duration-200 ${active ? 'text-accent/55' : 'text-fg-subtle'}`}>
                  {count}
                </span>
              )}
            </span>
          </button>
        );
      })}
    </div>
  );
}
