import ReactECharts from 'echarts-for-react';
import { Search, LineChart } from 'lucide-react';
import { SkeletonChart } from '../../../../shared/components/feedback/Skeleton';
import EmptyState from '../../../../shared/components/feedback/EmptyState';
import CompareInfoBar from '../CompareInfoBar';

export default function CompareChartArea({
  isLoading,
  seriesData,
  option,
  selected,
  targetCurrency,
  sharedBaselineDate,
  authoritativeReturns,
  t,
}) {
  return (
    <>
      <div className="relative rounded-xl border border-border-default/60 bg-bg-base/40 overflow-hidden h-[280px] sm:h-[380px] lg:h-[460px]">
        {isLoading && (
          <div className="absolute inset-0">
            <SkeletonChart h="100%" />
          </div>
        )}
        {!isLoading && seriesData.some((s) => s.points.length > 0) && (
          <ReactECharts option={option} style={{ height: '100%', width: '100%' }} opts={{ renderer: 'canvas' }} notMerge lazyUpdate />
        )}
        {!isLoading && (seriesData.length === 0 || seriesData.every((s) => s.points.length === 0)) && (
          <div className="absolute inset-0 flex items-center justify-center p-4">
            <EmptyState
              size="sm"
              className="border-none bg-transparent"
              icon={selected.length === 0
                ? <Search className="h-4 w-4 text-accent" />
                : <LineChart className="h-4 w-4 text-accent" />}
              message={selected.length === 0
                ? t('analytics.comparePickFirst', { defaultValue: 'En az 1 enstrüman seç' })
                : t('marketOverview.macro.noData', { defaultValue: 'Bu aralıkta veri yok' })}
            />
          </div>
        )}
      </div>

      {seriesData.length > 0 && <CompareInfoBar selected={seriesData} targetCurrency={targetCurrency} commonStartDate={sharedBaselineDate} authoritativeReturns={authoritativeReturns} t={t} />}
    </>
  );
}
