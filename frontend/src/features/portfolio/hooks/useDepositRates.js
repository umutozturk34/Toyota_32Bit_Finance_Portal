import { useMemo } from 'react';
import { useMacroIndicators } from '../../macro/hooks/useMacroIndicators';

const DEPOSIT_TERM_MATURITIES = ['M1', 'M3', 'M6', 'M12', 'M12_PLUS'];

// Months to advance the maturity date for each term bucket. M12_PLUS has no
// well-defined term (it is the open-ended "1Y+" bucket), so it is omitted and
// the caller leaves the maturity date for manual entry.
const MATURITY_TERM_MONTHS = { M1: 1, M3: 3, M6: 6, M12: 12 };

const ORDER = new Map(DEPOSIT_TERM_MATURITIES.map((m, i) => [m, i]));

export function useDepositRates(currency) {
  const { data, isLoading, isError } = useMacroIndicators({ category: 'DEPOSIT' });

  const rates = useMemo(() => {
    if (!Array.isArray(data) || !currency) return [];
    return data
      .filter(
        (i) => i.currency === currency
          && DEPOSIT_TERM_MATURITIES.includes(i.maturity)
          && i.lastValue != null,
      )
      .map((i) => ({
        code: i.code,
        maturity: i.maturity,
        lastValue: Number(i.lastValue),
        lastDate: i.lastDate,
        termMonths: MATURITY_TERM_MONTHS[i.maturity] ?? null,
      }))
      .sort((a, b) => (ORDER.get(a.maturity) ?? 99) - (ORDER.get(b.maturity) ?? 99));
  }, [data, currency]);

  return { rates, isLoading, isError };
}

export { MATURITY_TERM_MONTHS };
