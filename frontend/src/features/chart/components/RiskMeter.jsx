import { Flame } from 'lucide-react';

const RISK_SEGMENTS = [
  { band: 'LOW', key: 'chart.dataWindow.riskLow', hex: '#10b981' },
  { band: 'MEDIUM', key: 'chart.dataWindow.riskMedium', hex: '#f59e0b' },
  { band: 'HIGH', key: 'chart.dataWindow.riskHigh', hex: '#ef4444' },
];

// Risk = a green→amber→red spectrum with a marker showing exactly WHERE the asset's volatility (σ) falls, plus
// the active band badge. Far clearer than 3 discrete buttons — you read both the band AND the position at a glance.
// σ→position is piecewise to the band thresholds (LOW<25, MED 25–55, HIGH≥55 from chartAnalytics) so the marker
// always lands inside the matching zone.
export default function RiskMeter({ band, vol, t, locale }) {
  if (!band) return null;
  const seg = RISK_SEGMENTS.find((s) => s.band === band) || RISK_SEGMENTS[0];
  let pos = 50;
  if (vol != null) {
    if (vol < 25) pos = (vol / 25) * 33;
    else if (vol < 55) pos = 33 + ((vol - 25) / 30) * 33;
    else pos = 66 + Math.min(1, (vol - 55) / 45) * 34;
  }
  pos = Math.max(3, Math.min(97, pos));
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1 text-[11px] text-fg-muted">
          <Flame className="h-3 w-3 text-fg-subtle" />
          {t('chart.dataWindow.risk')}
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span
            className="rounded-md px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide"
            style={{ background: `${seg.hex}1f`, color: seg.hex, border: `1px solid ${seg.hex}66` }}
          >
            {t(seg.key)}
          </span>
          {vol != null && (
            <span className="font-mono text-[11px] tabular-nums text-fg-muted">σ {vol.toLocaleString(locale, { maximumFractionDigits: 1 })}%</span>
          )}
        </span>
      </div>
      <div className="relative h-2 rounded-full" style={{ background: 'linear-gradient(90deg, #10b981 0%, #f59e0b 50%, #ef4444 100%)' }}>
        <div
          className="absolute top-1/2 h-3.5 w-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-bg-elevated"
          style={{ left: `${pos}%`, background: seg.hex, boxShadow: `0 0 8px ${seg.hex}` }}
        />
      </div>
      <div className="flex justify-between text-[8px] font-mono uppercase tracking-wider text-fg-subtle">
        <span>{t('chart.dataWindow.riskLow')}</span>
        <span>{t('chart.dataWindow.riskMedium')}</span>
        <span>{t('chart.dataWindow.riskHigh')}</span>
      </div>
    </div>
  );
}
