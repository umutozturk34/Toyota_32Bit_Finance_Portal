
export const MARKET_CHIPS = [
  { id: 'STOCK', label: 'Hisse' },
  { id: 'FOREX', label: 'Döviz' },
  { id: 'FUND', label: 'Fon' },
  { id: 'COMMODITY', label: 'Emtia' },
  { id: 'BOND', label: 'Tahvil' },
  { id: 'NEWS', label: 'Haber' },
  { id: 'CRYPTO', label: 'Kripto' },
];

const CONTAINER_VARIANTS = { show: { transition: { staggerChildren: 0.04 } } };
const CHIP_VARIANTS = {
  hidden: { opacity: 0, scale: 0.92, y: 2 },
  show: { opacity: 1, scale: 1, y: 0 },
};

function CornerBrackets() {
  const c = 'absolute w-2 h-2 border-fg-subtle/30 pointer-events-none';
  return (
    <>
      <span aria-hidden className={`${c} top-1.5 left-1.5 border-l border-t`} />
      <span aria-hidden className={`${c} top-1.5 right-1.5 border-r border-t`} />
      <span aria-hidden className={`${c} bottom-1.5 left-1.5 border-l border-b`} />
      <span aria-hidden className={`${c} bottom-1.5 right-1.5 border-r border-b`} />
    </>
  );
}

/**
 * Terminal-HUD chip selector for the user's per-market opt-in to session
 * notifications.
 *
 * @typedef {Object} MarketSelectionChipsProps
 * @property {Set<string>} selected - market codes currently opted in (e.g. STOCK, FOREX)
 * @property {(next: Set<string>) => void} onToggle - called with the next selection set
 */

/** @param {MarketSelectionChipsProps} props */
export default function MarketSelectionChips({ selected, onToggle }) {
  const flip = (id) => {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onToggle(next);
  };
  return (
    <motion.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      className="relative rounded-xl border border-border-default bg-gradient-to-b from-bg-elevated/95 to-bg-base/30 px-4 py-3.5 overflow-hidden"
    >
      <CornerBrackets />

      <div className="relative flex items-center justify-between mb-3">
        <h3 className="font-mono text-[10px] tracking-[0.2em] uppercase text-fg-muted flex items-center gap-2">
          <span className="text-accent">▸</span>
          Hangi piyasalar
        </h3>
        <span className="font-mono text-[9px] tracking-[0.2em] text-fg-subtle uppercase tabular-nums">
          {String(selected.size).padStart(2, '0')}/{String(MARKET_CHIPS.length).padStart(2, '0')} aktif
        </span>
      </div>

      <motion.div
        variants={CONTAINER_VARIANTS}
        initial="hidden"
        animate="show"
        className="relative flex flex-wrap gap-1.5"
      >
        {MARKET_CHIPS.map(({ id, label }) => {
          const active = selected.has(id);
          return (
            <motion.button
              key={id}
              variants={CHIP_VARIANTS}
              transition={{ type: 'spring', stiffness: 380, damping: 26 }}
              whileTap={{ scale: 0.94 }}
              type="button"
              onClick={() => flip(id)}
              aria-pressed={active}
              className={`relative inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md font-mono text-[11px] tracking-[0.12em] uppercase cursor-pointer transition-colors duration-150
                ${active
                  ? 'border-2 border-double border-accent bg-accent/12 text-accent shadow-[inset_0_0_10px_-3px_var(--color-accent)]'
                  : 'border border-dashed border-border-default bg-transparent text-fg-muted hover:border-accent/40 hover:text-accent'}
              `}
            >
              <span aria-hidden className={`text-[8px] ${active ? 'text-accent' : 'text-fg-subtle'}`}>
                {active ? '◉' : '○'}
              </span>
              {label}
            </motion.button>
          );
        })}
      </motion.div>

      <p className="relative font-mono text-[9px] tracking-[0.18em] text-fg-subtle uppercase mt-3 leading-relaxed">
        // işaretsiz piyasalar açılış / veri bildirimi atlamaz
      </p>
    </motion.div>
  );
}
