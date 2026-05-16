import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import Card from '../../../shared/components/card';
import { useTheme } from '../../../shared/context/useTheme';

const PALETTE = [
  '#5E6AD2', '#10b981', '#f59e0b', '#ef4444',
  '#a855f7', '#06b6d4', '#84cc16', '#ec4899',
  '#facc15', '#22c55e',
];


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

export default function AllocationPie({ allocations }) {
  const { t } = useTranslation();
  const { isDark } = useTheme();
  const [hovered, setHovered] = useState(null);
  const centerFill = isDark ? '#0a0a0b' : '#ffffff';
  const centerSubtle = isDark ? 'rgba(255,255,255,0.55)' : 'rgba(15,23,42,0.55)';
  const centerLabel = isDark ? '#ffffff' : '#0f172a';
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
  if (arcs.length === 0) {
    return (
      <Card padding="md" radius="xl">
        <h3 className="text-sm font-semibold text-fg mb-2">{t('marketDetail.fund.allocationTitle')}</h3>
        <p className="text-xs text-fg-muted">{t('marketDetail.fund.allocationEmpty')}</p>
      </Card>
    );
  }
  return (
    <Card padding="md" radius="xl">
      <h3 className="text-sm font-semibold text-fg mb-3">{t('marketDetail.fund.allocationTitle')}</h3>
      <div className="flex justify-center mb-3" onMouseLeave={() => setHovered(null)}>
        <svg viewBox="0 0 100 100" className="w-40 h-40" style={{ filter: isDark ? 'drop-shadow(0 4px 12px rgba(94, 106, 210, 0.15))' : 'drop-shadow(0 4px 12px rgba(15, 23, 42, 0.08))' }}>
          {arcs.length === 1 ? (() => {
            const arc = arcs[0];
            const isHovered = hovered === arc.assetClass;
            const radius = isHovered ? 44 : 41;
            return (
              <circle
                cx="50" cy="50" r={radius}
                fill={arc.color}
                stroke={centerFill}
                strokeWidth="0.6"
                style={{ transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)', cursor: 'pointer' }}
                onMouseEnter={() => setHovered(arc.assetClass)}
              />
            );
          })() : arcs.map((arc, i) => {
            const isHovered = hovered === arc.assetClass;
            const isDimmed = hovered !== null && !isHovered;
            const a1 = (arc.start - 90) * Math.PI / 180;
            const a2 = (arc.end - 90) * Math.PI / 180;
            const radius = isHovered ? 44 : 41;
            const x1 = 50 + radius * Math.cos(a1);
            const y1 = 50 + radius * Math.sin(a1);
            const x2 = 50 + radius * Math.cos(a2);
            const y2 = 50 + radius * Math.sin(a2);
            const largeArc = arc.end - arc.start > 180 ? 1 : 0;
            return (
              <path
                key={i}
                d={`M 50 50 L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} Z`}
                fill={arc.color}
                fillOpacity={isDimmed ? 0.22 : 1}
                stroke={centerFill}
                strokeWidth="0.6"
                style={{ transition: 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)', cursor: 'pointer' }}
                onMouseEnter={() => setHovered(arc.assetClass)}
              />
            );
          })}
          <circle cx="50" cy="50" r="24" fill={centerFill} pointerEvents="none" />
          {hovered !== null ? (() => {
            const arc = arcs.find(a => a.assetClass === hovered);
            if (!arc) return null;
            return (
              <g pointerEvents="none">
                <text x="50" y="47" textAnchor="middle" dominantBaseline="central"
                      fontSize="11" fontWeight="700" fill={centerLabel}>
                  %{arc.pct}
                </text>
                <text x="50" y="56" textAnchor="middle" dominantBaseline="central"
                      fontSize="3.2" fill={centerSubtle} style={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  {arc.assetClass === '__other__'
                    ? t('marketDetail.fund.allocationOther')
                    : t(`assetClass.${arc.assetClass}`, { defaultValue: arc.assetClass.toUpperCase() })}
                </text>
              </g>
            );
          })() : (
            <g pointerEvents="none">
              <text x="50" y="47" textAnchor="middle" dominantBaseline="central"
                    fontSize="3.2" fill={centerSubtle} style={{ textTransform: 'uppercase', letterSpacing: '0.08em' }}>
                {arcs.length} {t('marketDetail.fund.allocationAssetCountLabel')}
              </text>
              <text x="50" y="55" textAnchor="middle" dominantBaseline="central"
                    fontSize="8" fontWeight="600" fill={centerLabel}>
                %100
              </text>
            </g>
          )}
        </svg>
      </div>
      <ul className="space-y-1" onMouseLeave={() => setHovered(null)}>
        {arcs.map((arc, i) => {
          const isHovered = hovered === arc.assetClass;
          return (
            <li
              key={i}
              className={`flex items-center justify-between gap-2 text-[11px] rounded-md px-1.5 py-1 transition-colors cursor-pointer ${
                isHovered ? 'bg-bg-base/80 ring-1 ring-border-default' : ''
              }`}
              onMouseEnter={() => setHovered(arc.assetClass)}
            >
              <span className="flex items-center gap-1.5 min-w-0">
                <span
                  className="h-2 w-2 rounded-sm shrink-0 transition-transform"
                  style={{
                    backgroundColor: arc.color,
                    transform: isHovered ? 'scale(1.4)' : 'scale(1)',
                  }}
                />
                <span className={`truncate transition-colors ${isHovered ? 'text-fg font-semibold' : 'text-fg-muted'}`}>
                  {arc.assetClass === '__other__'
                    ? t('marketDetail.fund.allocationOther')
                    : t(`assetClass.${arc.assetClass}`, { defaultValue: arc.assetClass.toUpperCase() })}
                </span>
              </span>
              <span className={`font-mono shrink-0 transition-colors ${isHovered ? 'text-fg font-semibold' : 'text-fg-muted'}`}>
                %{arc.pct}
              </span>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
