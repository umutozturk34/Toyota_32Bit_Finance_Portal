import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowUpNarrowWide, ArrowDownWideNarrow, SlidersHorizontal } from 'lucide-react';
import { Check } from './AnimatedIcons';

export default function SortSelect({ value, direction, options, onSortChange, onDirectionChange, showDefault = true }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const isDesc = direction === 'desc';
  const allOptions = showDefault ? [{ id: '', label: 'Varsayılan' }, ...options] : options;
  const activeLabel = allOptions.find(o => o.id === value)?.label || (showDefault ? 'Varsayılan' : options[0]?.label);

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div className="flex items-center gap-1.5">
      <div ref={ref} className="relative">
        <button
          onClick={() => setOpen(!open)}
          className={`flex items-center gap-2 rounded-2xl border bg-bg-elevated backdrop-blur-md px-3.5 py-2 cursor-pointer transition-all ${
            open ? 'border-accent/40 shadow-sm shadow-accent/10' : 'border-border-default hover:border-border-hover'
          }`}
        >
          <SlidersHorizontal className="h-3 w-3 text-fg-subtle" />
          <span className="text-xs font-semibold text-fg">{activeLabel}</span>
        </button>

        <AnimatePresence>
          {open && (
            <motion.div
              initial={{ opacity: 0, y: 6, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 6, scale: 0.96 }}
              transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
              className="absolute left-0 top-full mt-2 z-50 min-w-[180px] rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-xl p-1.5 shadow-xl"
              style={{ boxShadow: '0 12px 40px rgba(0,0,0,0.15), 0 0 0 1px rgba(99,102,241,0.06)' }}
            >
              <div className="space-y-0.5">
                {allOptions.map(({ id, label }) => (
                  <button
                    key={id}
                    onClick={() => { onSortChange(id); setOpen(false); }}
                    className={`flex items-center justify-between w-full rounded-xl px-3 py-2 text-xs font-medium transition-all cursor-pointer border-none ${
                      value === id
                        ? 'bg-accent/10 text-accent'
                        : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface/60'
                    }`}
                  >
                    <span>{label}</span>
                    {value === id && (
                      <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 500, damping: 25 }}>
                        <Check className="h-3 w-3" />
                      </motion.span>
                    )}
                  </button>
                ))}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {value && <motion.button
        onClick={() => onDirectionChange(isDesc ? 'asc' : 'desc')}
        whileTap={{ scale: 0.9 }}
        className="flex items-center justify-center w-9 h-9 rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md text-accent hover:border-accent/30 hover:shadow-sm hover:shadow-accent/10 transition-all cursor-pointer overflow-hidden"
      >
        <AnimatePresence mode="wait">
          <motion.div
            key={direction}
            initial={{ rotateX: isDesc ? -90 : 90, opacity: 0 }}
            animate={{ rotateX: 0, opacity: 1 }}
            exit={{ rotateX: isDesc ? 90 : -90, opacity: 0 }}
            transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
          >
            {isDesc
              ? <ArrowUpNarrowWide className="h-4 w-4" />
              : <ArrowDownWideNarrow className="h-4 w-4" />
            }
          </motion.div>
        </AnimatePresence>
      </motion.button>}
    </div>
  );
}
