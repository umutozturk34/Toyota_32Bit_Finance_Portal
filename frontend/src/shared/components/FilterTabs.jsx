import { motion } from 'framer-motion';

export default function FilterTabs({ items, activeId, onSelect, allLabel = 'Tümü', allCount, showAll = true, layoutId = 'filter-tab' }) {
  const allItems = showAll
    ? [{ type: 'ALL', label: allLabel, count: allCount }, ...items]
    : items;

  return (
    <div className="inline-flex gap-1 rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-1">
      {allItems.map(({ type, count, label }) => {
        const active = activeId === type;
        return (
          <button
            key={type}
            onClick={() => onSelect(type)}
            className="relative rounded-xl px-3.5 py-1.5 border-none cursor-pointer bg-transparent transition-colors"
          >
            {active && (
              <motion.span
                layoutId={layoutId}
                className="absolute inset-0 rounded-xl bg-accent/12 shadow-sm shadow-accent/10"
                style={{ borderRadius: 12 }}
                transition={{ type: 'spring', stiffness: 400, damping: 28 }}
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
