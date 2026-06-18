import { TrendingUp, TrendingDown, Medal } from 'lucide-react';
import { formatPercent } from '../../utils';
import HeroStat from '../HeroStat';

export default function ReturnsHeroStats({
  t, best, worst, positiveCount, negativeCount, nameFor, periodLabel, filtered,
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
      <HeroStat
        icon={<TrendingUp className="h-4 w-4" />}
        label={t('analytics.returns.topGainer')}
        value={positiveCount > 0
          ? <span className="text-success">{formatPercent(best.returnPct)}</span>
          : <span className="text-sm text-fg-muted">{t('analytics.returns.noGainers', { defaultValue: 'Bu dönemde kazanan yok' })}</span>}
        sub={positiveCount > 0 ? nameFor(best) : periodLabel}
        accent={positiveCount > 0 ? '#10b981' : '#6b7280'}
      />
      <HeroStat
        icon={<TrendingDown className="h-4 w-4" />}
        label={t('analytics.returns.topLoser')}
        value={negativeCount > 0
          ? <span className="text-danger">{formatPercent(worst.returnPct)}</span>
          : <span className="text-sm text-fg-muted">{t('analytics.returns.noLosers', { defaultValue: 'Bu dönemde kaybeden yok' })}</span>}
        sub={negativeCount > 0 ? nameFor(worst) : periodLabel}
        accent={negativeCount > 0 ? '#ef4444' : '#6b7280'}
      />
      <HeroStat
        icon={<Medal className="h-4 w-4" />}
        label={t('analytics.returns.profitLoss', { defaultValue: 'Kâr / Zarar' })}
        value={(
          <span className="inline-flex items-center gap-3 text-fg">
            <span className="inline-flex items-center gap-1.5">
              <TrendingUp className="h-5 w-5 text-success" />{positiveCount}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <TrendingDown className="h-5 w-5 text-danger" />{negativeCount}
            </span>
          </span>
        )}
        sub={`${filtered.length} ${t('analytics.returns.assetCount')} · ${periodLabel}`}
        accent="#5E6AD2"
      />
    </div>
  );
}
