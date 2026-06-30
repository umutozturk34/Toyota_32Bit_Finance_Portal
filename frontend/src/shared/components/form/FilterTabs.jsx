import { useTranslation } from 'react-i18next';

export default function FilterTabs({ items, activeId, onSelect, allLabel, allCount, showAll = true }) {
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
            {/* Active highlight as a CSS opacity cross-fade, NOT a framer-motion layoutId pill. The shared-layout
                pill animated its DOCUMENT position on any reflow — switching display currency reflows the numbers
                above, so the pill briefly flew out of its button and overlapped the neighbouring row. An always-
                mounted per-button span only fades in/out, so it can never chase a reflow. */}
            <span
              aria-hidden
              className={`pointer-events-none absolute inset-0 rounded-xl bg-accent/12 shadow-sm shadow-accent/10 transition-opacity duration-200 ${active ? 'opacity-100' : 'opacity-0'}`}
              style={{ borderRadius: 12 }}
            />
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
