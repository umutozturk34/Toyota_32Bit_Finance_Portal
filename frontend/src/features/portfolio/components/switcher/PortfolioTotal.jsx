import { useQuery } from '@tanstack/react-query';
import { portfolioService } from '../../services/portfolioService';
import { fixedIncomeService } from '../../services/fixedIncomeService';
import { useMoney } from '../../../../shared/hooks/useMoney';

// Each portfolio's current TRY total, fetched lazily only while the dropdown is open (so opening the switcher costs
// at most N small summary calls, never on every render). Spot and fixed-income read their own summary endpoint.
export default function PortfolioTotal({ portfolio, enabled }) {
  const isFixed = portfolio.type === 'FIXED';
  const { format: money } = useMoney({ lockBase: true });
  const { data } = useQuery({
    queryKey: ['switcherTotal', portfolio.id, portfolio.type],
    queryFn: () => (isFixed ? fixedIncomeService.summary(portfolio.id) : portfolioService.getSummary(portfolio.id)),
    enabled,
    staleTime: 60 * 1000,
  });
  const total = data?.totalValueTry ?? data?.totalValue ?? data?.value ?? null;
  if (total == null) return null;
  return (
    <span className="block text-[11px] font-mono tabular-nums text-fg-muted truncate">{money(total, 'TRY')}</span>
  );
}
