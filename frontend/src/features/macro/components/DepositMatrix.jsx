import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Banknote } from 'lucide-react';
import Card from '../../../shared/components/card';
import EmptyState from '../../../shared/components/feedback/EmptyState';
import { DEPOSIT_CURRENCIES, DEPOSIT_MATURITY_ORDER } from '../constants';
import { formatValue, themeFor } from '../utils';

const MATURITY_LABEL = {
  M1: '1M',
  M3: '3M',
  M6: '6M',
  M12: '1Y',
  M12_PLUS: '1Y+',
  TOTAL: 'Total',
};

export default function DepositMatrix({ indicators, onOpen }) {
  const { t } = useTranslation();
  const theme = themeFor('DEPOSIT');

  const byCurrencyMaturity = useMemo(() => {
    const map = new Map();
    indicators.forEach((i) => {
      if (i.category !== 'DEPOSIT' || !i.currency || !i.maturity) return;
      const key = `${i.currency}|${i.maturity}`;
      map.set(key, i);
    });
    return map;
  }, [indicators]);

  const allValues = useMemo(
    () => indicators
      .filter((i) => i.category === 'DEPOSIT' && i.lastValue != null)
      .map((i) => Number(i.lastValue)),
    [indicators]
  );
  const min = allValues.length ? Math.min(...allValues) : 0;
  const max = allValues.length ? Math.max(...allValues) : 1;
  const span = max - min || 1;

  if (byCurrencyMaturity.size === 0) {
    return (
      <EmptyState
        size="md"
        icon={<Banknote className="h-5 w-5 text-accent" />}
        title={t('marketOverview.macro.depositMatrixTitle', { defaultValue: 'Deposit Rate Matrix' })}
        message={t('marketOverview.macro.empty', { defaultValue: 'Henüz veri yok' })}
      />
    );
  }

  return (
    <Card variant="elevated" radius="xl" padding="lg" backdropBlur className="overflow-hidden">
      <div className="flex items-center gap-2 mb-4">
        <span className="h-2 w-2 rounded-full" style={{ background: theme.accent, boxShadow: `0 0 10px ${theme.glow}` }} />
        <h3 className="font-display text-sm font-bold uppercase tracking-[0.16em] text-fg">
          {t('marketOverview.macro.depositMatrixTitle', { defaultValue: 'Deposit Rate Matrix' })}
        </h3>
        <span className="text-[10px] font-mono text-fg-subtle uppercase tracking-[0.12em] ml-2">
          {t('marketOverview.macro.depositMatrixSub', { defaultValue: 'TCMB weekly · % nominal' })}
        </span>
      </div>

      <div className="overflow-x-auto -mx-1">
        <table className="w-full text-xs border-separate border-spacing-1 min-w-[520px]">
          <thead>
            <tr>
              <th className="text-left text-[10px] font-mono uppercase tracking-[0.14em] text-fg-muted py-1.5 pr-3">
                {t('marketOverview.macro.currency', { defaultValue: 'Currency' })}
              </th>
              {DEPOSIT_MATURITY_ORDER.map((m) => (
                <th key={m} className="text-center text-[10px] font-mono uppercase tracking-[0.14em] text-fg-muted py-1.5 px-1">
                  {MATURITY_LABEL[m]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {DEPOSIT_CURRENCIES.map((cur) => (
              <tr key={cur}>
                <td className="font-display font-bold text-fg text-sm pr-3 py-1.5">{cur}</td>
                {DEPOSIT_MATURITY_ORDER.map((m) => {
                  const ind = byCurrencyMaturity.get(`${cur}|${m}`);
                  if (!ind || ind.lastValue == null) {
                    return <td key={m} className="text-center font-mono text-fg-subtle text-[11px] py-1.5 px-1">—</td>;
                  }
                  const v = Number(ind.lastValue);
                  const heat = (v - min) / span;
                  const bgAlpha = (0.08 + heat * 0.32).toFixed(2);
                  return (
                    <td key={m} className="p-0.5">
                      <motion.button
                        type="button"
                        onClick={() => onOpen?.(ind)}
                        whileHover={{ y: -1 }}
                        whileTap={{ scale: 0.96 }}
                        className="w-full rounded-lg px-2 py-2 border cursor-pointer transition-colors"
                        style={{
                          background: `rgba(16,185,129,${bgAlpha})`,
                          borderColor: `rgba(16,185,129,${(0.18 + heat * 0.4).toFixed(2)})`,
                        }}
                      >
                        <span className="block font-mono tabular-nums font-bold text-fg text-[13px]">
                          {formatValue(v, 'PERCENT')}
                        </span>
                      </motion.button>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}
