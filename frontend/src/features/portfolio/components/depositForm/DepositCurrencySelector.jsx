import { useTranslation } from 'react-i18next';
import { Wallet } from 'lucide-react';
import { currencySymbolOf } from '../../../../shared/utils/priceCurrency';

export default function DepositCurrencySelector({ currencies, currency, onSelect }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-1.5 sm:col-span-2">
      <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
        <Wallet className="h-3 w-3" />
        {t('deposits.fields.currency')}
      </label>
      <div className="grid grid-cols-3 gap-1.5">
        {currencies.map((c) => (
          <button
            key={c}
            type="button"
            onClick={() => onSelect(c)}
            className={`flex items-center justify-center gap-1 rounded-lg py-2 text-xs font-semibold border transition-all cursor-pointer ${
              currency === c
                ? 'border-accent/40 bg-accent/10 text-accent'
                : 'border-border-default bg-bg-base text-fg-muted hover:text-fg'
            }`}
          >
            {currencySymbolOf(c)} {c}
          </button>
        ))}
      </div>
    </div>
  );
}
