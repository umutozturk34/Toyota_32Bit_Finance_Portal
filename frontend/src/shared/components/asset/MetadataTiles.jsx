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
      className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2"
    >
      {valid.map((t) => (
        <div
          key={t.label}
          className="rounded-lg border border-border-default bg-bg-elevated px-3 py-2 card-hover transition-all duration-200 hover:border-border-hover"
        >
          <p className="text-[10px] uppercase tracking-wider text-fg-muted">{t.label}</p>
          <div className={`text-sm font-mono font-semibold ${t.color || 'text-fg'}`}>{t.value}</div>
        </div>
      ))}
    </motion.div>
  );
}
