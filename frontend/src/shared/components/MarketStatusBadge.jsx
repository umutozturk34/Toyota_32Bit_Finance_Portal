import { CircleDot, CircleSlash } from 'lucide-react';
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

/**
 * @param {{ market: string, compact?: boolean }} props
 */
export default function MarketStatusBadge({ market, compact = false }) {
  const { entry } = useMarketSession(market);
  if (!entry) return null;
  const isOpen = entry.session === 'OPEN';
  const Icon = isOpen ? CircleDot : CircleSlash;
  const label = isOpen ? 'Açık' : 'Kapalı';
  const transitionLabel = relativeTransition(entry.nextTransitionAt);
  const tone = isOpen
    ? 'border-success/40 bg-success/15 text-success'
    : 'border-warning/40 bg-warning/15 text-warning';
  return (
    <span
      title={transitionLabel ? `${isOpen ? 'Kapanır' : 'Açılır'} ${transitionLabel}` : undefined}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] font-mono uppercase tracking-wider ${tone}`}
    >
      <Icon className="h-3 w-3" />
      <span>{label}</span>
      {!compact && transitionLabel ? (
        <span className="text-fg-subtle normal-case lowercase tracking-normal">· {transitionLabel}</span>
      ) : null}
    </span>
  );
}
