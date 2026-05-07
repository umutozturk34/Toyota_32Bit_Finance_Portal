import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Plus, Check } from 'lucide-react';
import { SECTION_DEFINITIONS, newSectionId } from './sections/sectionRegistry';

function buildAddOptions(sections) {
  const presentKinds = new Set(sections.filter((s) => s.visible).map((s) => s.kind));
  const hiddenById = new Map(
    sections.filter((s) => !s.visible).map((s) => [s.sectionId, s]),
  );
  const options = [];
  for (const [kind, def] of Object.entries(SECTION_DEFINITIONS)) {
    if (def.multiInstance) {
      options.push({ kind, mode: 'create', label: `Yeni ${def.label}`, def });
    } else {
      const hiddenInstance = sections.find((s) => s.kind === kind && !s.visible);
      if (hiddenInstance) {
        options.push({ kind, mode: 'reveal', sectionId: hiddenInstance.sectionId, label: def.label, def });
      } else if (!presentKinds.has(kind)) {
        options.push({ kind, mode: 'create', label: def.label, def });
      }
    }
  }
  hiddenById.forEach((section) => {
    if (section.kind === 'ASSET_CARDS') {
      options.push({
        kind: section.kind,
        mode: 'reveal',
        sectionId: section.sectionId,
        label: `${SECTION_DEFINITIONS.ASSET_CARDS.label} (gizli)`,
        def: SECTION_DEFINITIONS.ASSET_CARDS,
      });
    }
  });
  return options;
}

/**
 * @typedef {Object} AddWidgetMenuProps
 * @property {Array<{sectionId: string, kind: string, visible: boolean}>} sections
 * @property {(option: {mode: string, kind: string, sectionId?: string}) => void} onPick
 */

/** @param {AddWidgetMenuProps} props */
export default function AddWidgetMenu({ sections, onPick }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const options = useMemo(() => buildAddOptions(sections), [sections]);

  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const empty = options.length === 0;

  return (
    <div ref={ref} className="relative">
      <motion.button
        type="button"
        whileTap={{ scale: 0.96 }}
        onClick={() => !empty && setOpen((o) => !o)}
        disabled={empty}
        className={`flex items-center gap-1.5 rounded-lg border-2 border-dashed px-3 py-1.5 font-mono text-[10px] tracking-[0.18em] uppercase transition-colors cursor-pointer ${
          empty
            ? 'border-border-default bg-transparent text-fg-subtle cursor-not-allowed'
            : 'border-accent/50 bg-accent/5 text-accent hover:border-accent hover:bg-accent/10'
        }`}
      >
        <Plus className="h-3 w-3" />
        {empty ? 'Hepsi eklenmiş' : `+ Widget (${options.length})`}
      </motion.button>

      <AnimatePresence>
        {open && !empty && (
          <motion.div
            initial={{ opacity: 0, y: -4, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -4, scale: 0.97 }}
            transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
            className="absolute right-0 top-full mt-1.5 z-30 w-[280px] rounded-xl border border-accent/40 bg-bg-elevated shadow-2xl shadow-accent/15 backdrop-blur-md overflow-hidden"
          >
            <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/60 to-transparent" />
            <div className="px-3 py-2 border-b border-border-default">
              <p className="font-mono text-[9px] tracking-[0.2em] uppercase text-accent">▸ Widget ekle</p>
            </div>
            <div className="max-h-[300px] overflow-y-auto py-1">
              {options.map((opt, i) => {
                const Icon = opt.def.Icon;
                return (
                  <button
                    key={`${opt.kind}-${opt.sectionId ?? 'new'}-${i}`}
                    type="button"
                    onClick={() => {
                      onPick(opt);
                      setOpen(false);
                    }}
                    className="w-full flex items-start gap-2.5 px-3 py-2 hover:bg-accent/8 transition-colors cursor-pointer text-left border-none bg-transparent group"
                  >
                    <span className="flex items-center justify-center w-7 h-7 rounded-md bg-accent/10 text-accent shrink-0 group-hover:bg-accent/20 transition-colors">
                      <Icon className="h-3.5 w-3.5" />
                    </span>
                    <span className="flex-1 min-w-0">
                      <span className="block text-[11px] font-semibold text-fg leading-tight">{opt.label}</span>
                      <span className="block text-[9px] text-fg-muted mt-0.5 leading-relaxed truncate">{opt.def.description}</span>
                    </span>
                    <Check className="h-3 w-3 text-accent opacity-0 group-hover:opacity-100 transition-opacity shrink-0 mt-0.5" />
                  </button>
                );
              })}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export { buildAddOptions };
