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
      className="grid gap-2 grid-cols-3 min-[480px]:grid-cols-4 sm:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7"
    >
      {valid.map((t) => (
        <div
          key={t.label}
          className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2 card-hover transition-all duration-200 hover:border-border-hover min-w-0"
        >
          <p className="text-[10px] uppercase tracking-wider text-fg-muted truncate">{t.label}</p>
          <div className={`text-sm font-mono font-semibold truncate ${t.color || 'text-fg'}`} title={typeof t.value === 'string' ? t.value : undefined}>{t.value}</div>
        </div>
      ))}
    </motion.div>
  );
}
