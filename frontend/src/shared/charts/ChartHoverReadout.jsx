// Non-overlapping readout strip rendered BELOW a chart instead of a floating tooltip that covers
// the plot. Fully presentational: the chart passes a pre-formatted `date` and `fields` array; this
// component lays them out horizontally and WRAPS to more rows when there are many (e.g. a portfolio
// with lots of positions/lot-events at one point) so no chip is pushed off-screen — the old
// nowrap+overflow-x hid the overflow. The strip is a FIXED height (≈2 rows) with internal vertical
// scroll: keeping the height constant means hovering across points (where the chip count changes)
// never resizes the card / shifts the page — the previous min-height grew/shrank per point and made
// the layout jump. Two rows cover virtually every point; the rare 3+ row point scrolls in place.
// Pair it with `tooltip.showContent:false` on the ECharts option so the box is gone while the
// axisPointer crosshair stays.
function ChartHoverReadout({ date, fields = [] }) {
  if (!date && fields.length === 0) return null;
  return (
    <div className="flex flex-wrap content-start items-center gap-x-3 gap-y-1 px-3 sm:px-4 py-2 border-t border-border-default/50 text-[11px] h-[56px] overflow-y-auto">
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
