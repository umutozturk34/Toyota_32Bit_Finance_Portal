import { Coins } from 'lucide-react';
import Card from '../../../shared/components/card';
import CurrencyMarker from '../../../shared/components/currency/CurrencyMarker';

export default function BankCard({ row, t, displayCurrency, money }) {
  const spread = row.buyRate != null && row.sellRate != null
    ? Number(row.sellRate) - Number(row.buyRate)
    : null;
  const isMarket = row.bankCode === 'MARKET';
  const displayName = isMarket ? t('bankRates.marketBank') : row.bankName;
  const formatValue = (v) => v == null ? '—' : money(v);
  const formatSpread = (s) => s == null ? null : money(s);
  const marker = displayCurrency === 'ORIGINAL' ? 'TRY' : displayCurrency;
  return (
    <Card
      variant="elevated"
      radius="xl"
      padding="md"
      backdropBlur
      className="flex flex-col gap-3"
    >
      <div className="flex items-center gap-3">
        {row.bankLogoUrl ? (
          <img
            src={row.bankLogoUrl}
            alt={displayName}
            width={40}
            height={40}
            loading="lazy"
            className="w-10 h-10 rounded-lg object-contain"
            onError={(e) => { e.target.style.display = 'none'; }}
          />
        ) : (
          <div className={`w-10 h-10 rounded-lg flex items-center justify-center text-xs font-bold ${
            isMarket ? 'bg-warning/15 text-warning' : 'bg-surface text-fg-muted'
          }`}>
            {isMarket ? <Coins className="h-5 w-5" /> : (row.bankCode || '?').slice(0, 3).toUpperCase()}
          </div>
        )}
        <div className="min-w-0">
          <p className="text-sm font-semibold text-fg truncate">{displayName}</p>
          {isMarket && (
            <p className="text-[10px] text-fg-muted truncate">{t('bankRates.marketHint')}</p>
          )}
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <div className="rounded-lg border border-success/25 bg-success/5 px-2.5 py-2">
          <p className="text-[10px] text-success uppercase tracking-wide font-medium flex items-center gap-1">
            {t('bankRates.buy')} <CurrencyMarker code={marker} />
          </p>
          <p className="text-sm font-mono font-bold text-success">{formatValue(row.buyRate)}</p>
        </div>
        <div className="rounded-lg border border-danger/25 bg-danger/5 px-2.5 py-2">
          <p className="text-[10px] text-danger uppercase tracking-wide font-medium flex items-center gap-1">
            {t('bankRates.sell')} <CurrencyMarker code={marker} />
          </p>
          <p className="text-sm font-mono font-bold text-danger">{formatValue(row.sellRate)}</p>
        </div>
      </div>
      {spread != null && (
        <div className="flex items-center justify-between pt-0.5 font-mono text-[10px]">
          <span className="uppercase tracking-wide text-fg-subtle">{t('bankRates.spread')}</span>
          <span className="font-semibold tabular-nums text-fg-muted">{formatSpread(spread)}</span>
        </div>
      )}
    </Card>
  );
}
