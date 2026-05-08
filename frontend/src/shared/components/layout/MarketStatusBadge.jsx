import { motion } from 'framer-motion';
import { Clock } from 'lucide-react';
import { useMarketSession } from '../../hooks/useMarketStatus';

const TRANSITION_FORMATTER = new Intl.RelativeTimeFormat('tr-TR', { numeric: 'auto' });
const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

function relativeTransition(iso) {
  if (!iso) return null;
  const target = new Date(iso).getTime();
  if (Number.isNaN(target)) return null;
  const delta = target - Date.now();
  const abs = Math.abs(delta);
  if (abs >= DAY_MS) return TRANSITION_FORMATTER.format(Math.round(delta / DAY_MS), 'day');
  if (abs >= HOUR_MS) return TRANSITION_FORMATTER.format(Math.round(delta / HOUR_MS), 'hour');
  return TRANSITION_FORMATTER.format(Math.round(delta / MINUTE_MS), 'minute');
}

function StatusDot({ isOpen }) {
  if (isOpen) {
    return (
      <span className="relative inline-flex w-1.5 h-1.5 shrink-0" aria-hidden>
        <span className="absolute inset-0 rounded-full bg-success opacity-50 animate-ping" />
        <span className="relative inline-block w-1.5 h-1.5 rounded-full bg-success shadow-[0_0_6px_rgba(74,222,128,0.6)]" />
      </span>
    );
  }
  return <span aria-hidden className="inline-block w-1.5 h-1.5 rounded-full bg-fg-subtle/40 shrink-0" />;
}

/**
 * Compact session pill — themed to match the cards (rounded, subtle gradient,
 * accent-tinted border on OPEN). Hover reveals a card-style detail panel.
 *
 * @param {{ market: string, compact?: boolean }} props
 */
export default function MarketStatusBadge({ market, compact = false }) {
  const { entry } = useMarketSession(market);
  if (!entry) return null;
  const isOpen = entry.session === 'OPEN';
  const transitionLabel = relativeTransition(entry.nextTransitionAt);
  const action = isOpen ? 'kapanır' : 'açılır';

  return (
    <motion.div
      initial={{ opacity: 0, y: -3 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
      className="group relative inline-flex"
    >
      <div
        className={`relative inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border bg-bg-elevated/90 backdrop-blur-sm transition-colors ${
          isOpen
            ? 'border-success/40 shadow-[0_0_14px_-6px_rgba(74,222,128,0.55)]'
            : 'border-border-default'
        }`}
      >
        <StatusDot isOpen={isOpen} />
        <span className={`font-display text-[11px] font-bold tracking-tight leading-none ${isOpen ? 'text-success' : 'text-fg-muted'}`}>
          {isOpen ? 'Açık' : 'Kapalı'}
        </span>
        {!compact && transitionLabel && (
          <>
            <span aria-hidden className="text-fg-subtle/40 leading-none">·</span>
            <span className="font-mono text-[10px] tabular-nums text-fg-subtle leading-none">
              {transitionLabel.replace(/^./, (c) => c.toUpperCase())}
            </span>
          </>
        )}
      </div>

      <div
        className="absolute left-0 top-full mt-1.5 z-50 min-w-[12rem] rounded-lg border border-border-default bg-bg-elevated/95 backdrop-blur-md p-2.5 shadow-xl shadow-black/20
                   opacity-0 -translate-y-1 pointer-events-none
                   transition-[opacity,transform] duration-200 ease-out
                   group-hover:opacity-100 group-hover:translate-y-0"
      >
        <div className="flex items-center justify-between mb-1.5">
          <span className="font-display text-[11px] font-bold text-fg">{market}</span>
          <span className="font-mono text-[9px] tracking-[0.16em] uppercase text-fg-subtle">SESSION</span>
        </div>
        <div className={`flex items-center gap-1.5 mb-1.5 ${isOpen ? 'text-success' : 'text-fg-muted'}`}>
          <StatusDot isOpen={isOpen} />
          <span className="font-display text-[12px] font-semibold leading-none">
            {isOpen ? 'Aktif seans' : 'Seans dışı'}
          </span>
        </div>
        {transitionLabel && (
          <div className="flex items-center gap-1.5 text-[10px] text-fg-muted">
            <Clock className="h-3 w-3 text-fg-subtle" />
            <span><span className="text-fg-subtle">{action}</span> <span className="text-fg font-semibold">{transitionLabel}</span></span>
          </div>
        )}
      </div>
    </motion.div>
  );
}
