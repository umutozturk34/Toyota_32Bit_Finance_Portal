import { useTranslation } from 'react-i18next';
import Card from '../../../shared/components/card';
import { visibleDecimals } from '../../../shared/utils/formatters';

const PERIODS = [
  { key: 'return1m', labelKey: 'marketDetail.fund.return.1m' },
  { key: 'return3m', labelKey: 'marketDetail.fund.return.3m' },
  { key: 'return6m', labelKey: 'marketDetail.fund.return.6m' },
  { key: 'returnYtd', labelKey: 'marketDetail.fund.return.ytd' },
  { key: 'return1y', labelKey: 'marketDetail.fund.return.1y' },
  { key: 'return3y', labelKey: 'marketDetail.fund.return.3y' },
  { key: 'return5y', labelKey: 'marketDetail.fund.return.5y' },
];

// Each period is a tinted tile: green/red by sign, with a fill bar whose length is the return magnitude
// normalised to the strongest period on the card — so the best/worst stretches pop at a glance.
export default function ReturnsList({ metadata }) {
  const { t } = useTranslation();
  if (!metadata) return null;
  const rows = PERIODS.filter((p) => metadata[p.key] != null)
    .map((p) => ({ ...p, value: Number(metadata[p.key]) }));
  if (rows.length === 0) return null;
  const maxAbs = Math.max(...rows.map((r) => Math.abs(r.value)), 0.01);

  return (
    <Card padding="md" radius="xl">
      <h3 className="mb-3 text-sm font-semibold text-fg">{t('marketDetail.fund.returnsTitle')}</h3>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {rows.map((p) => {
          const positive = p.value >= 0;
          const tone = positive ? 'var(--color-success)' : 'var(--color-danger)';
          const fillPct = Math.max(6, (Math.abs(p.value) / maxAbs) * 100);
          return (
            <div
              key={p.key}
              className="relative overflow-hidden rounded-xl border border-border-default bg-bg-base/40 px-2.5 py-2"
            >
              <span
                aria-hidden
                className="absolute inset-y-0 left-0"
                style={{ width: `${fillPct}%`, background: `linear-gradient(90deg, color-mix(in srgb, ${tone} 18%, transparent), transparent)` }}
              />
              <p className="relative text-[10px] font-semibold uppercase tracking-wider text-fg-muted">{t(p.labelKey)}</p>
              <p className="relative mt-0.5 font-mono text-sm font-bold tabular-nums" style={{ color: tone }}>
                {positive ? '+' : ''}{p.value.toFixed(visibleDecimals(p.value, 2))}%
              </p>
            </div>
          );
        })}
      </div>
    </Card>
  );
}
