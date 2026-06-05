// Non-overlapping readout strip rendered BELOW a chart instead of a floating tooltip that covers
// the plot. Fully presentational: the chart passes a pre-formatted `date` and `fields` array; this
// component only lays them out horizontally (wraps on mobile). Pair it with `tooltip.showContent:false`
// on the ECharts option so the covering box is gone while the axisPointer crosshair stays.
function ChartHoverReadout({ date, fields = [] }) {
  if (!date && fields.length === 0) return null;
  return (
    <div className="flex items-center gap-x-3 px-3 sm:px-4 py-2 border-t border-border-default/50 text-[11px] min-h-[38px] overflow-x-auto whitespace-nowrap [scrollbar-width:thin]">
      {date && <span className="text-fg-muted font-mono shrink-0">{date}</span>}
      {fields.map((f) => (
        <span key={f.key} className="inline-flex items-center gap-1 shrink-0">
          {f.dot && <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ background: f.dot }} />}
          {f.label && <span className="text-fg/80">{f.label}</span>}
          <span
            className={`font-mono font-semibold ${
              f.tone === 'pos' ? 'text-success' : f.tone === 'neg' ? 'text-danger' : 'text-fg'
            }`}
          >
            {f.value}
          </span>
        </span>
      ))}
    </div>
  );
}

export default ChartHoverReadout;
