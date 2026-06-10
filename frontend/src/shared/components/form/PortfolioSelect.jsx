import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Wallet, ChevronDown } from 'lucide-react';
import { Check } from '../feedback/AnimatedIcons';

/**
 * Shared portfolio picker for the position modals (spot + derivative). Replaces the raw native <select> that
 * clashed with the glass/card design system. variant="auto" shows a segmented control for a few portfolios and a
 * glass dropdown once there are more, so it stays tidy whether the user has two portfolios or twenty.
 */
export default function PortfolioSelect({ portfolios = [], value, onChange, label, variant = 'auto' }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const useTabs = variant === 'tabs' || (variant === 'auto' && portfolios.length <= 3);
  const active = portfolios.find((p) => String(p.id) === String(value)) || portfolios[0];

  useEffect(() => {
    if (useTabs) return undefined;
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [useTabs]);

  const pick = (id) => { onChange?.(Number(id)); setOpen(false); };

  return (
    <div className="space-y-1.5">
      {label && (
        <span className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
          <Wallet className="h-3 w-3" />
          {label}
        </span>
      )}

      {useTabs ? (
        <div className="flex max-w-full gap-1 overflow-x-auto scrollbar-hide rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md p-1">
          {portfolios.map((p) => {
            const isActive = String(p.id) === String(value);
            return (
              <button
                key={p.id}
                type="button"
                onClick={() => pick(p.id)}
                className="relative flex-1 min-w-0 rounded-xl px-3 py-2 min-h-9 border-none cursor-pointer bg-transparent transition-colors"
              >
                {isActive && (
                  <motion.span
                    layoutId="portfolio-select-tab"
                    className="absolute inset-0 rounded-xl bg-accent/12 shadow-sm shadow-accent/10"
                    style={{ borderRadius: 12 }}
                    transition={{ type: 'spring', stiffness: 400, damping: 28 }}
                  />
                )}
                <span className={`relative z-10 block truncate text-xs font-semibold tracking-tight transition-colors ${isActive ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
                  {p.name}
                </span>
              </button>
            );
          })}
        </div>
      ) : (
        <div ref={ref} className="relative">
          <button
            type="button"
            onClick={() => setOpen((o) => !o)}
            className={`flex w-full items-center justify-between gap-2 rounded-2xl border bg-bg-elevated backdrop-blur-md px-3.5 py-2.5 min-h-11 cursor-pointer transition-all ${
              open ? 'border-accent/40 shadow-sm shadow-accent/10' : 'border-border-default hover:border-border-hover'
            }`}
          >
            <span className="flex items-center gap-2 min-w-0">
              <Wallet className="h-3.5 w-3.5 text-fg-subtle shrink-0" />
              <span className="truncate text-sm font-semibold text-fg">{active?.name}</span>
            </span>
            <ChevronDown className={`h-4 w-4 text-fg-subtle shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
          </button>

          <AnimatePresence>
            {open && (
              <motion.div
                initial={{ opacity: 0, y: 6, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 6, scale: 0.96 }}
                transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
                className="absolute left-0 right-0 top-full mt-2 z-50 max-h-60 overflow-y-auto scrollbar-thin rounded-2xl border border-border-default p-1.5"
                style={{ background: 'var(--color-bg-base)', boxShadow: '0 12px 40px rgba(0,0,0,0.18), 0 0 0 1px rgba(99,102,241,0.08)' }}
              >
                <div className="space-y-0.5">
                  {portfolios.map((p) => {
                    const isActive = String(p.id) === String(value);
                    return (
                      <button
                        key={p.id}
                        type="button"
                        onClick={() => pick(p.id)}
                        className={`flex items-center justify-between w-full rounded-xl px-3 py-2 text-sm font-medium transition-all cursor-pointer border-none ${
                          isActive ? 'bg-accent/10 text-accent' : 'bg-transparent text-fg-muted hover:text-fg hover:bg-surface/60'
                        }`}
                      >
                        <span className="truncate">{p.name}</span>
                        {isActive && (
                          <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 500, damping: 25 }}>
                            <Check className="h-3.5 w-3.5 shrink-0" />
                          </motion.span>
                        )}
                      </button>
                    );
                  })}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
