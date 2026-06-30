import { RANGE_OPTIONS } from './rangeSelectorOptions';

const SIZES = {
  sm: { container: 'p-0.5 rounded-lg gap-1', item: 'rounded-md px-2.5 py-1 text-[11px] font-medium', highlight: 'rounded-md' },
  md: { container: 'p-1 rounded-xl gap-0.5', item: 'rounded-lg px-3 py-1.5 text-[11px] font-semibold', highlight: 'rounded-lg' },
};

export default function RangeSelector({ value, onChange, size = 'sm', options = RANGE_OPTIONS, idMap }) {
  const sz = SIZES[size];
  const resolved = idMap ? options.map((o) => ({ ...o, id: idMap[o.id] ?? o.id })) : options;
  return (
    <div className={`inline-flex max-w-full items-center overflow-x-auto border border-border-default bg-bg-base ${sz.container}`}>
      {resolved.map(({ id, label }) => {
        const active = value === id;
        return (
          <button
            key={id}
            type="button"
            onClick={() => onChange(id)}
            className={`relative shrink-0 ${sz.item} transition-all border-none cursor-pointer bg-transparent`}
          >
            {/* CSS opacity highlight instead of a framer-motion layoutId pill — the shared-layout pill chased its
                document position on unrelated reflows (e.g. switching display currency) and overlapped the row
                next to it. An always-mounted per-button span only cross-fades, so it stays put on reflow. */}
            <span
              aria-hidden
              className={`pointer-events-none absolute inset-0 ${sz.highlight} bg-accent/15 transition-opacity duration-200 ${active ? 'opacity-100' : 'opacity-0'}`}
            />
            <span className={`relative z-10 ${active ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>
              {label}
            </span>
          </button>
        );
      })}
    </div>
  );
}
