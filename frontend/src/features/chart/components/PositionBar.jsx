// Low → current → high range position: a danger→warning→success gradient with a marker at the close's percentile
// between the period low and high. Returns null when the position can't be computed.
export default function PositionBar({ pct, lowLabel, highLabel }) {
  if (pct == null) return null;
  const clamped = Math.max(0, Math.min(100, pct));
  return (
    <div className="space-y-1">
      <div className="relative h-1.5 rounded-full bg-gradient-to-r from-danger/40 via-warning/40 to-success/40">
        <div
          className="absolute top-1/2 h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-bg-elevated bg-accent shadow"
          style={{ left: `${clamped}%` }}
        />
      </div>
      <div className="flex justify-between text-[10px] font-mono text-fg-subtle">
        <span className="truncate">{lowLabel}</span>
        <span className="truncate">{highLabel}</span>
      </div>
    </div>
  );
}
