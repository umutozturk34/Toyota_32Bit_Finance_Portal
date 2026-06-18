import { TrendingUp, TrendingDown, Trophy } from 'lucide-react';
import HeroStat from '../BeaterHeroStat';
import { formatPercent } from '../../utils';

export default function BeaterHeroStats({
  t, period, data, scopeBeating, scopeTotal, scopeLosing, winRate, benchmarkLabel,
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
      <HeroStat
        icon={<Trophy className="h-4 w-4" />}
        label={t('analytics.beatingBenchmark', { defaultValue: 'Yenenler' })}
        value={`${scopeBeating}/${scopeTotal}`}
        sub={`${winRate}%`}
        accent="#10b981"
      />
      <HeroStat
        icon={<TrendingUp className="h-4 w-4" />}
        label={t('analytics.benchmarkReturn', { defaultValue: 'Benchmark getirisi' })}
        value={data.benchmarkReturnPct != null ? formatPercent(data.benchmarkReturnPct) : '—'}
        sub={`${benchmarkLabel} · ${period}${data.comparisonCurrency ? ` · ${data.comparisonCurrency}` : ''}`}
        accent="#f59e0b"
      />
      <HeroStat
        icon={<TrendingDown className="h-4 w-4" />}
        label={t('analytics.losers', { defaultValue: 'Altta kalan' })}
        value={scopeLosing > 0
          ? `${scopeLosing}`
          : <span className="text-sm text-fg-muted">{t('analytics.noUnderperformers', { defaultValue: 'Altta kalan yok' })}</span>}
        sub={scopeLosing > 0
          ? t('analytics.realLoss', { defaultValue: 'Excess return < 0' })
          : t('analytics.allBeatSub', { defaultValue: 'Hepsi göstergeyi yendi' })}
        accent={scopeLosing > 0 ? '#ef4444' : '#6b7280'}
      />
    </div>
  );
}
