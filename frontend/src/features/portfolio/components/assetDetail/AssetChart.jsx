import { useMemo } from 'react';
import ReactECharts from 'echarts-for-react';
import { buildAssetChartOption } from '../../lib/assetChartBuilder';

export function AssetChart({ data, isDark, t, convertAt, displayCurrency, nativeCurrency }) {
  const option = useMemo(
    () => buildAssetChartOption(data, isDark, t, convertAt, displayCurrency, nativeCurrency),
    [data, isDark, t, convertAt, displayCurrency, nativeCurrency]
  );
  if (!option) return null;
  return <ReactECharts option={option} notMerge lazyUpdate className="h-[260px] sm:h-[300px] lg:h-[340px]" opts={{ renderer: 'canvas' }} />;
}
