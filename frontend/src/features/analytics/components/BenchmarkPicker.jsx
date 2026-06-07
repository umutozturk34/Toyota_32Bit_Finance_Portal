import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronDown, Check, Search, X } from 'lucide-react';

const GROUP_DEFS = [
  { id: 'INFLATION', labelKey: 'analytics.bmGroupInflation', accent: '#f59e0b' },
  { id: 'RATES',     labelKey: 'analytics.bmGroupRates',     accent: '#5E6AD2' },
  { id: 'TRY',       labelKey: 'analytics.bmGroupDepositTRY', accent: '#10b981' },
  { id: 'USD',       labelKey: 'analytics.bmGroupDepositUSD', accent: '#06b6d4' },
  { id: 'EUR',       labelKey: 'analytics.bmGroupDepositEUR', accent: '#8b5cf6' },
];
const MATURITY_ORDER = ['M1', 'M3', 'M6', 'M12', 'M12_PLUS', 'TOTAL'];

function bucketOf(opt) {
  if (opt.category === 'INFLATION') return 'INFLATION';
  if (opt.category === 'RATES') return 'RATES';
  if (opt.category === 'DEPOSIT') return opt.currency || 'TRY';
  return 'RATES';
}

function sortInBucket(items) {
  return [...items].sort((a, b) => {
    const ai = MATURITY_ORDER.indexOf(a.maturity);
    const bi = MATURITY_ORDER.indexOf(b.maturity);
    if (ai !== -1 || bi !== -1) {
      if (ai === -1) return 1;
      if (bi === -1) return -1;
      return ai - bi;
    }
    return String(a.code).localeCompare(String(b.code));
  });
}

function labelOf(opt, t) {
  return t(`marketOverview.macro.${opt.label}`, { defaultValue: opt.label });
}

export default function BenchmarkPicker({ value, onChange, options, t, defaultLabel }) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [activeIdx, setActiveIdx] = useState(-1);
  const containerRef = useRef(null);
  const searchRef = useRef(null);
  const panelRef = useRef(null);

  const grouped = useMemo(() => {
    const buckets = new Map(GROUP_DEFS.map((g) => [g.id, []]));
    for (const opt of options || []) {
      const b = bucketOf(opt);
      if (!buckets.has(b)) buckets.set(b, []);
      buckets.get(b).push(opt);
    }
    return GROUP_DEFS
      .map((g) => ({ ...g, items: sortInBucket(buckets.get(g.id) || []) }))
      .filter((g) => g.items.length > 0);
  }, [options]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return grouped;
    return grouped
      .map((g) => ({
        ...g,
        items: g.items.filter((opt) => {
          const lbl = (opt.label || '').toLowerCase();
          const code = (opt.code || '').toLowerCase();
          return lbl.includes(q) || code.includes(q);
        }),
      }))
      .filter((g) => g.items.length > 0);
  }, [grouped, search]);

  const flatItems = useMemo(
    () => filtered.flatMap((g) => g.items.map((opt) => ({ opt, accent: g.accent }))),
    [filtered]
  );

  const activeOption = useMemo(() => {
    if (!value) return null;
    return (options || []).find((o) => o.code === value) || null;
  }, [value, options]);

  const activeAccent = useMemo(() => {
    if (!activeOption) return '#6366f1';
    const groupId = bucketOf(activeOption);
    return GROUP_DEFS.find((g) => g.id === groupId)?.accent || '#6366f1';
  }, [activeOption]);

  const close = useCallback(() => {
    setOpen(false);
    setSearch('');
    setActiveIdx(-1);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) close();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, close]);

  useEffect(() => {
    if (open) {
      const id = setTimeout(() => searchRef.current?.focus(), 80);
      return () => clearTimeout(id);
    }
    return undefined;
  }, [open]);

  useEffect(() => {
    if (activeIdx < 0 || !panelRef.current) return;
    const el = panelRef.current.querySelector(`[data-idx="${activeIdx}"]`);
    if (el) el.scrollIntoView({ block: 'nearest' });
  }, [activeIdx]);

  function handleKey(e) {
    if (!open) {
      if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
        e.preventDefault();
        setOpen(true);
      }
      return;
    }
    if (e.key === 'Escape') {
      e.preventDefault();
      close();
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIdx((i) => Math.min(flatItems.length - 1, i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIdx((i) => Math.max(0, i - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const target = activeIdx >= 0 ? flatItems[activeIdx] : flatItems[0];
      if (target) {
        onChange(target.opt.code);
        close();
      }
    }
  }

  function pick(code) {
    onChange(code);
    close();
  }

  function clearValue(e) {
    e.stopPropagation();
    onChange('');
  }

  const triggerLabel = activeOption ? labelOf(activeOption, t) : defaultLabel;
  const triggerCode = activeOption?.code;

  return (
    <div ref={containerRef} className="relative" onKeyDown={handleKey}>
      <button
        type="button"
        onClick={() => setOpen((s) => !s)}
        className="group inline-flex items-center gap-2.5 rounded-lg border border-border-default bg-bg-elevated px-3 py-1.5 text-xs hover:border-accent/40 transition-colors cursor-pointer w-full sm:w-auto sm:min-w-[240px] max-w-full sm:max-w-[320px]"
        style={{ borderColor: open ? `${activeAccent}66` : undefined }}
      >
        <span
          className="h-2 w-2 rounded-full shrink-0"
          style={{ background: activeAccent, boxShadow: `0 0 8px ${activeAccent}80` }}
        />
        <span className="flex-1 min-w-0 text-left flex items-baseline gap-2">
          <span className="font-semibold text-fg truncate">{triggerLabel}</span>
          {triggerCode && (
            <span className="font-mono text-[10px] text-fg-subtle tracking-[0.05em] truncate">{triggerCode}</span>
          )}
        </span>
        {activeOption && (
          <span
            onClick={clearValue}
            className="h-4 w-4 flex items-center justify-center rounded-sm text-fg-subtle hover:text-fg hover:bg-bg-base/80 transition-colors cursor-pointer"
            role="button"
            tabIndex={-1}
          >
            <X className="h-3 w-3" />
          </span>
        )}
        <ChevronDown
          className={`h-3.5 w-3.5 text-fg-muted transition-transform shrink-0 ${open ? 'rotate-180' : ''}`}
        />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            ref={panelRef}
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.18, ease: [0.32, 0.72, 0, 1] }}
            className="absolute z-[80] mt-2 left-0 w-[min(360px,calc(100vw-32px))] max-w-[calc(100vw-32px)] max-h-[480px] rounded-2xl border border-border-default overflow-hidden flex flex-col"
            style={{
              background: 'var(--color-bg-deep)',
              backdropFilter: 'blur(20px)',
              WebkitBackdropFilter: 'blur(20px)',
              boxShadow: '0 24px 60px -16px rgba(0,0,0,0.65), 0 0 0 1px rgba(99,102,241,0.12), 0 0 80px -12px rgba(99,102,241,0.18)',
            }}
          >
            <div className="h-px bg-gradient-to-r from-transparent via-accent/50 to-transparent" />

            <div className="relative shrink-0 px-3 pt-3 pb-2 border-b border-border-default/40">
              <Search className="absolute left-5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-fg-muted pointer-events-none" />
              <input
                ref={searchRef}
                value={search}
                onChange={(e) => { setSearch(e.target.value); setActiveIdx(-1); }}
                placeholder={t('analytics.bmSearchPlaceholder', { defaultValue: 'Benchmark ara…' })}
                className="w-full bg-bg-base/60 border border-border-default rounded-lg pl-8 pr-3 py-1.5 text-xs font-mono text-fg placeholder:text-fg-subtle focus:outline-none focus:border-accent/60 focus:ring-1 focus:ring-accent/30 transition-colors"
              />
            </div>

            <div className="flex-1 overflow-y-auto py-1.5">
              {filtered.length === 0 ? (
                <div className="px-4 py-8 text-center text-xs text-fg-muted font-mono italic">
                  {t('analytics.bmNoResults', { defaultValue: 'Eşleşme yok' })}
                </div>
              ) : (
                filtered.map((g, gIdx) => {
                  const baseIdx = filtered.slice(0, gIdx).reduce((acc, x) => acc + x.items.length, 0);
                  return (
                    <div key={g.id} className="mb-2 last:mb-0">
                      <div className="flex items-center gap-2 px-3 mb-0.5">
                        <span className="h-1 w-1 rounded-full" style={{ background: g.accent }} />
                        <span className="text-[9px] font-mono uppercase tracking-[0.22em] text-fg-muted font-bold">
                          {t(g.labelKey, { defaultValue: g.id })}
                        </span>
                        <span className="flex-1 h-px bg-gradient-to-r from-border-default/40 to-transparent ml-1" />
                        <span className="text-[9px] font-mono text-fg-subtle tabular-nums">
                          {String(g.items.length).padStart(2, '0')}
                        </span>
                      </div>
                      <div className="px-1.5">
                        {g.items.map((opt, iIdx) => {
                          const idx = baseIdx + iIdx;
                          const isActive = opt.code === value;
                          const isHovered = idx === activeIdx;
                          return (
                            <button
                              key={opt.code}
                              type="button"
                              data-idx={idx}
                              onMouseEnter={() => setActiveIdx(idx)}
                              onClick={() => pick(opt.code)}
                              className="w-full flex items-center gap-2.5 px-2 py-2 rounded-lg text-left transition-colors border-none cursor-pointer"
                              style={{
                                background: isActive
                                  ? `${g.accent}1f`
                                  : isHovered ? 'rgba(255,255,255,0.04)' : 'transparent',
                                boxShadow: isActive ? `inset 0 0 0 1px ${g.accent}40` : 'none',
                              }}
                            >
                              <span
                                className="h-1.5 w-1.5 rounded-full shrink-0"
                                style={{
                                  background: g.accent,
                                  boxShadow: isActive ? `0 0 6px ${g.accent}` : 'none',
                                }}
                              />
                              <span className="flex-1 min-w-0">
                                <span
                                  className={`block text-[12px] truncate ${
                                    isActive ? 'text-fg font-semibold' : 'text-fg'
                                  }`}
                                >
                                  {labelOf(opt, t)}
                                </span>
                                <span className="block text-[10px] font-mono text-fg-subtle tracking-[0.05em] truncate">
                                  {opt.code}
                                </span>
                              </span>
                              {isActive && (
                                <Check className="h-3.5 w-3.5 shrink-0" style={{ color: g.accent }} />
                              )}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {!value && (
              <div className="shrink-0 border-t border-border-default/40 px-3 py-2 text-[10px] font-mono text-fg-subtle italic flex items-center gap-1.5">
                <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
                {t('analytics.bmDefaultHint', { defaultValue: 'Varsayılan: TÜFE (seçim yapılmadı)' })}
              </div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
