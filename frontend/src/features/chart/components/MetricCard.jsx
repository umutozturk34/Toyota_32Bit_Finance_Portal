// Tonal left-accent stat card — scans faster than a flat label/value row. tone/accent are supplied by the
// caller (derived from the value's sign) so the card stays presentational.
export default function MetricCard({ label, value, tone, accent }) {
  return (
    <div
      className="rounded-lg border border-l-2 border-border-default/60 bg-bg-base/40 px-2.5 py-1 min-w-0"
      style={{ borderLeftColor: accent || 'var(--color-border-default)' }}
    >
      <div className="text-[9px] font-medium uppercase tracking-wide text-fg-subtle truncate">{label}</div>
      <div className={`font-mono text-sm font-bold tabular-nums truncate ${tone || 'text-fg'}`}>{value}</div>
    </div>
  );
}
