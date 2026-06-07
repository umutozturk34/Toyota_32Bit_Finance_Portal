export default function HeroStat({ icon, label, value, sub, accent }) {
  return (
    <div
      className="rounded-xl border border-border-default px-3 sm:px-4 py-3 sm:py-3.5"
      style={{ background: `${accent}0d` }}
    >
      <div className="flex items-center gap-2 mb-2 text-xs font-display font-semibold tracking-tight" style={{ color: accent }}>
        {icon}
        <span>{label}</span>
      </div>
      <div className="font-display text-xl sm:text-2xl lg:text-3xl font-bold text-fg tabular-nums leading-none">{value}</div>
      <div className="mt-1.5 text-xs font-mono text-fg-subtle">{sub}</div>
    </div>
  );
}
