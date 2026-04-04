import { motion } from 'framer-motion';
import { TABS, CATEGORY_CONFIG } from './newsConfig.jsx';

export default function NewsFilters({ activeTab, onTabChange, categoryCounts }) {
    return (
        <motion.div
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.08 }}
            className="flex gap-1.5 flex-wrap p-1.5 rounded-xl border border-border-default bg-bg-elevated"
        >
            {TABS.map((tab) => {
                const cfg = CATEGORY_CONFIG[tab];
                const Icon = cfg.icon;
                const count = categoryCounts[tab] ?? 0;
                const isActive = activeTab === tab;
                return (
                    <button
                        key={tab}
                        id={`news-tab-${tab.toLowerCase()}`}
                        onClick={() => onTabChange(tab)}
                        className={`
                            flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                            transition-all duration-150 cursor-pointer border-none whitespace-nowrap
                            ${isActive
                                ? 'bg-accent text-white shadow-[0_1px_4px_rgba(94,106,210,0.4)]'
                                : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface'
                            }
                        `}
                    >
                        <Icon size={13} strokeWidth={2} className={isActive ? 'text-white' : 'text-fg-subtle'} />
                        {cfg.label}
                        {count > 0 && (
                            <span className={`text-[10px] rounded-full px-1.5 py-0.5 font-medium ${isActive ? 'bg-white/20 text-white' : 'bg-surface text-fg-subtle'}`}>
                                {count}
                            </span>
                        )}
                    </button>
                );
            })}
        </motion.div>
    );
}
