import { motion } from 'framer-motion';
import { cardVariants } from '../../utils/animations';

export default function MetadataTiles({ tiles = [] }) {
  const valid = tiles.filter(Boolean);
  if (!valid.length) return null;
  return (
    <motion.div
      variants={cardVariants}
      initial="hidden"
      animate="show"
      className="overflow-hidden rounded-xl border border-border-default bg-bg-elevated grid grid-cols-2 min-[480px]:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8"
    >
      {valid.map((t) => (
        <div
          key={t.label}
          className="border-r border-b border-border-default/50 px-3 py-2.5 min-w-0 transition-colors hover:bg-surface/50"
        >
          <p className="text-[10px] uppercase tracking-wider text-fg-subtle truncate">{t.label}</p>
          <div
            className={`mt-1 text-sm font-mono font-semibold truncate ${t.color || 'text-fg'}`}
            title={typeof t.value === 'string' ? t.value : undefined}
          >
            {t.value}
          </div>
        </div>
      ))}
    </motion.div>
  );
}
