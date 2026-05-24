import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import Card from '../../../shared/components/card';

const CURRENCY_ORDER = ['TRY', 'USD', 'EUR'];
const MATURITY_ORDER = ['M1', 'M3', 'M6', 'M12', 'M12_PLUS', 'TOTAL'];

function maturityLabel(m, t) {
  const key = {
    M1: '1 Ay',
    M3: '3 Ay',
    M6: '6 Ay',
    M12: '1 Yıl',
    M12_PLUS: '1 Yıl+',
    TOTAL: 'Toplam',
  };
  return t(`marketOverview.macro.maturity${m}`, { defaultValue: key[m] || m });
}

export default function YieldCurvePanel({ indicators = [] }) {
  const { t } = useTranslation();

  const grouped = useMemo(() => {
    const byCurrency = {};
    indicators.filter((i) => i.category === 'DEPOSIT').forEach((indicator) => {
      const currency = indicator.currency || '?';
      if (!byCurrency[currency]) byCurrency[currency] = {};
      byCurrency[currency][indicator.maturity] = indicator;
    });
    return byCurrency;
  }, [indicators]);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-3 sm:gap-4">
      {CURRENCY_ORDER.map((currency) => {
        const row = grouped[currency] || {};
        return (
          <Card
            key={currency}
            as={motion.div}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            variant="elevated"
            radius="xl"
            padding="md"
            backdropBlur
            className="flex flex-col gap-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-fg">{t('marketOverview.macro.savingsCardTitle', { currency })}</h3>
              <span className="text-[10px] font-mono text-fg-muted uppercase tracking-wide">
                {t('marketOverview.macro.categoryDeposit')}
              </span>
            </div>
            <div className="space-y-1.5">
              {MATURITY_ORDER.map((maturity) => {
                const indicator = row[maturity];
                const value = indicator?.lastValue;
                const isTotal = maturity === 'TOTAL';
                return (
                  <div
                    key={maturity}
                    className={`flex items-center justify-between rounded-lg px-3 py-2 text-xs ${
                      isTotal ? 'bg-accent/10 font-semibold' : 'bg-bg-elevated/40'
                    }`}
                  >
                    <span className={isTotal ? 'text-accent' : 'text-fg-muted'}>
                      {maturityLabel(maturity, t)}
                    </span>
                    <span className={`font-mono ${isTotal ? 'text-accent text-sm' : 'text-fg'}`}>
                      {value != null ? `%${Number(value).toFixed(2)}` : '—'}
                    </span>
                  </div>
                );
              })}
            </div>
          </Card>
        );
      })}
    </div>
  );
}
