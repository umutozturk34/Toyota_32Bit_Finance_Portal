import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from '../../../shared/components/card';
import { useTheme } from '../../../shared/context/useTheme';

const PALETTE = [
  '#5E6AD2', '#10b981', '#f59e0b', '#ef4444',
  '#a855f7', '#06b6d4', '#84cc16', '#ec4899',
  '#facc15', '#22c55e',
];

const VIEW = 100;
const CENTER = VIEW / 2;
const OUTER_RADIUS = 42;
const HOVER_RADIUS = 44.5;
const INNER_RADIUS = 26;
const SLICE_GAP_DEG = 0.6;

function classify(rows) {
  if (!rows || rows.length === 0) return [];
  const positive = rows.filter(r => Number(r.percentage) > 0);
  const sorted = [...positive].sort((a, b) => Number(b.percentage) - Number(a.percentage));
  const top = sorted.slice(0, 8);
  const rest = sorted.slice(8);
  if (rest.length > 0) {
    const otherSum = rest.reduce((s, r) => s + Number(r.percentage), 0);
    if (otherSum > 0) top.push({ assetClass: '__other__', percentage: otherSum });
  }
  return top;
}

function arcPath(startDeg, endDeg, radius) {
  const a1 = (startDeg - 90) * Math.PI / 180;
  const a2 = (endDeg - 90) * Math.PI / 180;
  const x1 = CENTER + radius * Math.cos(a1);
  const y1 = CENTER + radius * Math.sin(a1);
  const x2 = CENTER + radius * Math.cos(a2);
  const y2 = CENTER + radius * Math.sin(a2);
  const ix1 = CENTER + INNER_RADIUS * Math.cos(a1);
  const iy1 = CENTER + INNER_RADIUS * Math.sin(a1);
  const ix2 = CENTER + INNER_RADIUS * Math.cos(a2);
  const iy2 = CENTER + INNER_RADIUS * Math.sin(a2);
  const largeArc = endDeg - startDeg > 180 ? 1 : 0;
  return `M ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} L ${ix2} ${iy2} A ${INNER_RADIUS} ${INNER_RADIUS} 0 ${largeArc} 0 ${ix1} ${iy1} Z`;
}

export default function AllocationPie({ allocations }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const [hovered, setHovered] = useState(null);
  const data = useMemo(() => classify(allocations), [allocations]);
  const arcs = useMemo(() => {
    if (data.length === 0) return [];
    const total = data.reduce((s, r) => s + Number(r.percentage), 0);
    return data.reduce((acc, row, idx) => {
      const fraction = Number(row.percentage) / total;
      const prevEnd = acc.length === 0 ? 0 : acc[acc.length - 1].endFraction;
      const endFraction = prevEnd + fraction;
      acc.push({
        ...row,
        color: PALETTE[idx % PALETTE.length],
        start: prevEnd * 360,
        end: endFraction * 360,
        endFraction,
        pct: (fraction * 100).toFixed(1),
      });
      return acc;
    }, []);
  }, [data]);

  const labelFor = (assetClass) =>
    assetClass === '__other__'
      ? t('marketDetail.fund.allocationOther')
      : t(`assetClass.${assetClass}`, { defaultValue: assetClass.toUpperCase() });

  if (arcs.length === 0) {
    return (
      <Card padding="md" radius="xl">
        <h3 className="text-sm font-semibold text-fg mb-2">{t('marketDetail.fund.allocationTitle')}</h3>
        <p className="text-xs text-fg-muted">{t('marketDetail.fund.allocationEmpty')}</p>
      </Card>
    );
  }

  const hoveredArc = hovered != null ? arcs.find(a => a.assetClass === hovered) : null;

  return (
    <Card padding="md" radius="xl">
      <h3 className="text-sm font-semibold text-fg mb-3">{t('marketDetail.fund.allocationTitle')}</h3>
      <div className="flex justify-center mb-4" onMouseLeave={() => setHovered(null)}>
        <div className="relative w-44 h-44">
          <svg
            viewBox={`0 0 ${VIEW} ${VIEW}`}
            className="w-full h-full"
            style={{ filter: isDark ? 'drop-shadow(0 6px 18px rgba(94,106,210,0.18))' : 'drop-shadow(0 6px 18px rgba(15,23,42,0.10))' }}
          >
            <defs>
              {arcs.map((arc, i) => (
                <radialGradient key={i} id={`alloc-grad-${i}`} cx="50%" cy="50%" r="55%">
                  <stop offset="55%" stopColor={arc.color} stopOpacity="1" />
                  <stop offset="100%" stopColor={arc.color} stopOpacity="0.78" />
                </radialGradient>
              ))}
            </defs>
            {arcs.length === 1 ? (() => {
              const arc = arcs[0];
              const isHovered = hovered === arc.assetClass;
              const r = isHovered ? HOVER_RADIUS : OUTER_RADIUS;
              return (
                <g
                  style={{ cursor: 'pointer' }}
                  onMouseEnter={() => setHovered(arc.assetClass)}
                >
                  <circle
                    cx={CENTER} cy={CENTER} r={r}
                    fill={`url(#alloc-grad-0)`}
                    style={{ transition: 'all 0.22s cubic-bezier(0.4, 0, 0.2, 1)' }}
                  />
                  <circle cx={CENTER} cy={CENTER} r={INNER_RADIUS} fill={isDark ? '#0a0a0b' : '#ffffff'} />
                </g>
              );
            })() : arcs.map((arc, i) => {
              const isHovered = hovered === arc.assetClass;
              const isDimmed = hovered !== null && !isHovered;
              const radius = isHovered ? HOVER_RADIUS : OUTER_RADIUS;
              const gap = arc.end - arc.start > SLICE_GAP_DEG * 2 ? SLICE_GAP_DEG : 0;
              const d = arcPath(arc.start + gap / 2, arc.end - gap / 2, radius);
              return (
                <path
                  key={i}
                  d={d}
                  fill={`url(#alloc-grad-${i})`}
                  fillOpacity={isDimmed ? 0.28 : 1}
                  style={{ transition: 'all 0.22s cubic-bezier(0.4, 0, 0.2, 1)', cursor: 'pointer' }}
                  onMouseEnter={() => setHovered(arc.assetClass)}
                />
              );
            })}
          </svg>
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="flex flex-col items-center justify-center w-[55%] text-center">
              {hoveredArc ? (
                <>
                  <span className="font-mono tabular-nums font-bold text-fg text-2xl leading-none">
                    {hoveredArc.pct}<span className="text-fg-muted text-base align-top ml-0.5">%</span>
                  </span>
                  <span
                    className="mt-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-fg-muted leading-tight line-clamp-2"
                    title={labelFor(hoveredArc.assetClass)}
                  >
                    {labelFor(hoveredArc.assetClass)}
                  </span>
                </>
              ) : (
                <>
                  <span className="text-[10px] font-semibold uppercase tracking-[0.12em] text-fg-muted">
                    {arcs.length} {t('marketDetail.fund.allocationAssetCountLabel')}
                  </span>
                  <span className="mt-1 font-mono tabular-nums font-bold text-fg text-xl leading-none">
                    100<span className="text-fg-muted text-sm align-top ml-0.5">%</span>
                  </span>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
      <ul className="space-y-0.5" onMouseLeave={() => setHovered(null)}>
        {arcs.map((arc, i) => {
          const isHovered = hovered === arc.assetClass;
          return (
            <li
              key={i}
              className={`group/row flex items-center justify-between gap-2 text-[11px] rounded-md px-2 py-1.5 transition-all cursor-pointer ${
                isHovered ? 'bg-bg-base/80 ring-1 ring-inset ring-border-default/80' : 'hover:bg-bg-base/40'
              }`}
              onMouseEnter={() => setHovered(arc.assetClass)}
            >
              <span className="flex items-center gap-2 min-w-0">
                <span
                  className="h-2.5 w-2.5 rounded-sm shrink-0 transition-transform"
                  style={{
                    backgroundColor: arc.color,
                    transform: isHovered ? 'scale(1.25)' : 'scale(1)',
                    boxShadow: isHovered ? `0 0 6px ${arc.color}80` : 'none',
                  }}
                />
                <span className={`truncate transition-colors ${isHovered ? 'text-fg font-semibold' : 'text-fg-muted group-hover/row:text-fg/85'}`}>
                  {labelFor(arc.assetClass)}
                </span>
              </span>
              <span className={`font-mono tabular-nums shrink-0 transition-colors ${isHovered ? 'text-fg font-semibold' : 'text-fg-muted'}`}>
                {arc.pct}<span className="text-fg-subtle ml-0.5">%</span>
              </span>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
