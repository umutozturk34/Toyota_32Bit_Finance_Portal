import { motion } from 'framer-motion';
import { useMarketSession } from '../hooks/useMarketStatus';

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

function formatCountdown(iso) {
  if (!iso) return '--:--';
  const delta = Math.max(0, new Date(iso).getTime() - Date.now());
  const totalMinutes = Math.floor(delta / MINUTE_MS);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${String(hours).padStart(2, '0')}h${String(minutes).padStart(2, '0')}`;
}

function CornerBrackets() {
  const c = 'absolute w-1.5 h-1.5 border-fg-subtle/30 pointer-events-none';
  return (
    <>
      <span aria-hidden className={`${c} top-0.5 left-0.5 border-l border-t`} />
      <span aria-hidden className={`${c} top-0.5 right-0.5 border-r border-t`} />
      <span aria-hidden className={`${c} bottom-0.5 left-0.5 border-l border-b`} />
      <span aria-hidden className={`${c} bottom-0.5 right-0.5 border-r border-b`} />
    </>
  );
}

function StatusDot({ isOpen }) {
  if (isOpen) {
    return (
      <span className="relative inline-flex w-2 h-2 shrink-0" aria-hidden>
        <span className="absolute inset-0 rounded-full bg-current opacity-40 animate-ping" />
        <span className="relative inline-block w-2 h-2 rounded-full bg-current shadow-[0_0_8px_currentColor]" />
      </span>
    );
  }
  return (
    <span aria-hidden className="inline-block w-2 h-2 rounded-full border border-current opacity-50 shrink-0" />
  );
}

/**
 * Terminal-inspired HUD pill: pulsing live dot, segmented monospace structure
 * with ASCII corner brackets and divider, hover-expanded session detail.
 *
 * @param {{ market: string, compact?: boolean }} props
 */
export default function MarketStatusBadge({ market, compact = false }) {
  const { entry } = useMarketSession(market);
  if (!entry) return null;
  const isOpen = entry.session === 'OPEN';
  const transitionLabel = relativeTransition(entry.nextTransitionAt);
  const countdown = formatCountdown(entry.nextTransitionAt);
  const tone = isOpen ? 'text-success' : 'text-warning';
  const action = isOpen ? 'kapanır' : 'açılır';
  const sheen = isOpen
    ? 'shadow-[inset_0_0_0_1px_rgba(74,222,128,0.10),0_0_18px_-6px_rgba(74,222,128,0.45)]'
    : 'shadow-[inset_0_0_0_1px_rgba(245,158,11,0.06)]';

  return (
    <motion.div
      initial={{ opacity: 0, y: -3 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      className="group relative inline-flex"
    >
      <div
        className={`relative inline-flex items-center gap-2 px-2.5 py-1 rounded-md border border-border-default bg-gradient-to-b from-bg-elevated to-bg-base/70 font-mono text-[10px] uppercase tracking-[0.1em] ${sheen}`}
      >
        <CornerBrackets />
        <span className={`relative flex items-center gap-1.5 font-bold ${tone}`}>
          <StatusDot isOpen={isOpen} />
          {isOpen ? 'OPEN' : 'CLOSED'}
        </span>
        {!compact && entry.nextTransitionAt && (
          <>
            <span aria-hidden className="relative text-fg-subtle/60 select-none">│</span>
            <span className="relative inline-flex items-baseline gap-1">
              <span className="text-[8px] tracking-[0.2em] text-fg-subtle">T—</span>
              <span className="tabular-nums font-semibold text-fg">{countdown}</span>
            </span>
          </>
        )}
      </div>

      <div
        className="absolute left-0 top-full mt-2 z-50 min-w-[13rem] rounded-md border border-border-default bg-bg-elevated/95 backdrop-blur-md p-3 shadow-2xl
                   opacity-0 -translate-y-1 pointer-events-none
                   transition-[opacity,transform] duration-200 ease-out
                   group-hover:opacity-100 group-hover:translate-y-0"
      >
        <div className="flex items-center justify-between text-[9px] tracking-[0.2em] text-fg-subtle uppercase mb-1.5 font-mono">
          <span className="text-accent">▸ {market}</span>
          <span>// session</span>
        </div>
        <div className={`text-[13px] font-bold font-mono mb-1 flex items-center gap-1.5 ${tone}`}>
          <StatusDot isOpen={isOpen} />
          {isOpen ? 'AKTİF SEANS' : 'SEANS DIŞI'}
        </div>
        {transitionLabel && (
          <div className="text-[11px] text-fg-muted font-mono leading-relaxed">
            <span className="text-fg-subtle">↪ {action}</span>{' '}
            <span className="text-fg">{transitionLabel}</span>
          </div>
        )}
      </div>
    </motion.div>
  );
}
