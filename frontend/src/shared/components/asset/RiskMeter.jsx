import { useTranslation } from 'react-i18next';
import Card from '../card';

// SRRI-style 1–7 risk scale rendered as a colour gradient bar (green → red). The fund's level lights up while
// the rest dims, and a marker labels the active band — far more legible than a row of dots for the detail page.
const SEGMENT_COLORS = ['#10b981', '#34d399', '#facc15', '#f59e0b', '#fb923c', '#f43f5e', '#e11d48'];

export default function RiskMeter({ value }) {
  const { t } = useTranslation();
  if (value == null) return null;
  const level = Math.max(1, Math.min(7, Number(value)));
  const active = SEGMENT_COLORS[level - 1];

  return (
    <Card padding="md" radius="xl">
      <div className="mb-3 flex items-baseline justify-between gap-2">
        <h3 className="text-sm font-semibold text-fg">{t('marketDetail.fund.riskLabel')}</h3>
        <span className="font-mono text-sm font-bold tabular-nums" style={{ color: active }}>
          {level}<span className="text-fg-subtle">/7</span>
        </span>
      </div>
      <div className="flex items-end gap-1">
        {SEGMENT_COLORS.map((color, i) => {
          const on = i + 1 <= level;
          const isCurrent = i + 1 === level;
          return (
            <div key={i} className="flex flex-1 flex-col items-center gap-1">
              <span
                className="w-full rounded-sm transition-all"
                style={{
                  height: isCurrent ? 22 : 14,
                  backgroundColor: on ? color : 'var(--color-border-default)',
                  opacity: on ? (isCurrent ? 1 : 0.55) : 1,
                  boxShadow: isCurrent ? `0 0 12px -2px ${color}` : 'none',
                }}
              />
              <span className={`text-[9px] font-bold tabular-nums ${isCurrent ? 'text-fg' : 'text-fg-subtle'}`}>{i + 1}</span>
            </div>
          );
        })}
      </div>
      <div className="mt-1 flex justify-between text-[10px] font-medium uppercase tracking-wider text-fg-subtle">
        <span>{t('marketDetail.fund.riskLow')}</span>
        <span>{t('marketDetail.fund.riskHigh')}</span>
      </div>
    </Card>
  );
}
