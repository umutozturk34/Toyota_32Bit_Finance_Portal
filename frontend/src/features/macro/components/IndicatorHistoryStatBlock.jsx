export default function StatBlock({ label, value, sub, accent, highlight }) {
  return (
    <div
      className={`rounded-lg px-3 py-2 border ${highlight ? '' : 'border-border-default/60 bg-bg-base/40'}`}
      style={highlight ? { background: `${accent}10`, borderColor: `${accent}40` } : {}}
    >
      <p className="text-[9px] font-mono uppercase tracking-[0.14em] text-fg-muted">{label}</p>
      <p className="mt-0.5 font-mono tabular-nums font-bold text-fg text-base">{value}</p>
      {sub && <p className="text-[10px] text-fg-subtle font-mono mt-0.5">{sub}</p>}
    </div>
  );
}
