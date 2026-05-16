const RISK_COLORS = [
  'bg-emerald-500',
  'bg-emerald-400',
  'bg-yellow-400',
  'bg-amber-400',
  'bg-orange-400',
  'bg-rose-400',
  'bg-rose-500',
];

const TEXT_COLORS = [
  'text-emerald-400',
  'text-emerald-300',
  'text-yellow-300',
  'text-amber-300',
  'text-orange-300',
  'text-rose-300',
  'text-rose-400',
];

export default function RiskBadge({ value, label = 'R', size = 'sm', className = '' }) {
  if (value == null) return null;
  const clamped = Math.max(1, Math.min(7, Number(value)));
  const dotSize = size === 'lg' ? 'h-1.5 w-1.5' : 'h-1 w-1';
  const textSize = size === 'lg' ? 'text-sm' : 'text-[10px]';
  const accent = TEXT_COLORS[clamped - 1];
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md border border-border-default/60 bg-bg-base/40 px-1.5 py-0.5 ${className}`}
      title={`Risk ${clamped}/7`}
    >
      <span className={`${textSize} font-semibold tracking-wide ${accent}`}>{label}:{clamped}</span>
      <span className="flex items-center gap-[2px]">
        {Array.from({ length: 7 }).map((_, i) => (
          <span
            key={i}
            className={`${dotSize} rounded-full ${i < clamped ? RISK_COLORS[clamped - 1] : 'bg-border-default/60'}`}
          />
        ))}
      </span>
    </span>
  );
}
