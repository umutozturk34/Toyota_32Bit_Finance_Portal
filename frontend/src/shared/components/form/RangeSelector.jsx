
export const RANGE_OPTIONS = [
  { id: '1M', label: '1A', months: 1 },
  { id: '3M', label: '3A', months: 3 },
  { id: '6M', label: '6A', months: 6 },
  { id: '1Y', label: '1Y', months: 12 },
  { id: '5Y', label: '5Y', months: 60 },
  { id: 'ALL', label: 'Maks', months: 0, alias: 'MAX' },
];

const SIZES = {
  sm: { container: 'p-0.5 rounded-lg gap-1', item: 'rounded-md px-2.5 py-1 text-[11px] font-medium', highlight: 'rounded-md' },
  md: { container: 'p-1 rounded-xl gap-0.5', item: 'rounded-lg px-3 py-1.5 text-[11px] font-semibold', highlight: 'rounded-lg' },
};

export default function RangeSelector({ value, onChange, layoutId, size = 'sm', options = RANGE_OPTIONS, idMap }) {
  const sz = SIZES[size];
  const resolved = idMap ? options.map((o) => ({ ...o, id: idMap[o.id] ?? o.id })) : options;
  return (
    <div className={`inline-flex items-center border border-border-default bg-bg-base ${sz.container}`}>
      {resolved.map(({ id, label }) => {
        const active = value === id;
        return (
          <button
            key={id}
            type="button"
            onClick={() => onChange(id)}
            className={`relative ${sz.item} transition-all border-none cursor-pointer bg-transparent`}
          >
            {active && (
              <motion.span
                layoutId={layoutId}
                className={`absolute inset-0 ${sz.highlight} bg-accent/15`}
                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
              />
            )}
            <span className={`relative z-10 ${active ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
              {label}
            </span>
          </button>
        );
      })}
    </div>
  );
}
