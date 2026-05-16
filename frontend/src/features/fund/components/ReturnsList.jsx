import { useTranslation } from 'react-i18next';
import Card from '../../../shared/components/card';

const PERIODS = [
  { key: 'return1m', labelKey: 'marketDetail.fund.return.1m' },
  { key: 'return3m', labelKey: 'marketDetail.fund.return.3m' },
  { key: 'return6m', labelKey: 'marketDetail.fund.return.6m' },
  { key: 'returnYtd', labelKey: 'marketDetail.fund.return.ytd' },
  { key: 'return1y', labelKey: 'marketDetail.fund.return.1y' },
  { key: 'return3y', labelKey: 'marketDetail.fund.return.3y' },
  { key: 'return5y', labelKey: 'marketDetail.fund.return.5y' },
];

export default function ReturnsList({ metadata }) {
  const { t } = useTranslation();
  if (!metadata) return null;
  const rows = PERIODS.filter(p => metadata[p.key] != null);
  if (rows.length === 0) return null;
  return (
    <Card padding="md" radius="xl">
      <h3 className="text-sm font-semibold text-fg mb-3">{t('marketDetail.fund.returnsTitle')}</h3>
      <ul className="space-y-1.5">
        {rows.map(p => {
          const v = Number(metadata[p.key]);
          const positive = v >= 0;
          return (
            <li key={p.key} className="flex items-center justify-between gap-2 text-xs">
              <span className="text-fg-muted">{t(p.labelKey)}</span>
              <span className={`font-mono font-semibold ${positive ? 'text-emerald-400' : 'text-rose-400'}`}>
                {positive ? '+' : ''}{v.toFixed(2)}%
              </span>
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
