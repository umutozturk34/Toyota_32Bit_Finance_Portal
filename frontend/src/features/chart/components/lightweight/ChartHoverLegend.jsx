import React from 'react';
import { useTranslation } from 'react-i18next';
import { visibleDecimals } from '../../../../shared/utils/formatters';

// Fixed resting spot for the static on-chart OHLC legend (top-left).
const LEGEND_DEFAULT_CLASS = 'left-1 top-1 sm:left-2 sm:top-2';

// On-chart hover legend (TradingView-style): the hovered day's OHLC/price + every OPEN overlay indicator's
// value at that point + open sub-charts' (RSI/MACD) values. Purely presentational — it reads the already-resolved
// legend values handed down by LightweightChart and never touches the chart instance or its refs.
const ChartHoverLegend = ({
    legendVals, legendDate, fmtHover,
    allowCandle, chartType, legendHasForexLegs, legendOverlayInds,
    hasRSI, subValues, rsiIndicator, hasMACD, macdIndicator,
}) => {
    const { t } = useTranslation();
    if (!legendVals) return null;
    return (
        <div className={`absolute ${LEGEND_DEFAULT_CLASS} z-[12] pointer-events-none max-w-[78%] sm:max-w-[calc(100%-1rem)] rounded-md border border-border-default bg-bg-base/85 backdrop-blur-sm px-1.5 sm:px-2.5 py-1 sm:py-1.5 shadow-lg`}>
            <div className="flex flex-wrap items-center gap-x-1.5 sm:gap-x-2.5 gap-y-0 sm:gap-y-0.5 font-mono text-[9px] sm:text-[10px] leading-tight">
                {legendDate && <span className="text-fg-subtle">{legendDate}</span>}
                {allowCandle && chartType === 'candle' ? (
                    <>
                        <span className="text-fg-subtle">O <span className="text-fg">{fmtHover(legendVals.open)}</span></span>
                        <span className="text-fg-subtle">H <span className="text-success">{fmtHover(legendVals.high)}</span></span>
                        <span className="text-fg-subtle">L <span className="text-danger">{fmtHover(legendVals.low)}</span></span>
                        <span className="text-fg-subtle">C <span className="text-fg">{fmtHover(legendVals.close)}</span></span>
                    </>
                ) : legendHasForexLegs ? (
                    <>
                        <span className="text-fg-subtle">{t('chart.toolbar.crosshair.sell')} <span className="text-fg">{fmtHover(legendVals.sellingPrice)}</span></span>
                        <span className="text-fg-subtle">{t('chart.toolbar.crosshair.buy')} <span className="text-fg">{fmtHover(legendVals.buyingPrice)}</span></span>
                        {legendVals.effectiveSellingPrice != null && legendVals.effectiveSellingPrice !== legendVals.sellingPrice && (
                            <span className="text-fg-subtle">{t('chart.toolbar.crosshair.effSell')} <span className="text-fg">{fmtHover(legendVals.effectiveSellingPrice)}</span></span>
                        )}
                        {legendVals.effectiveBuyingPrice != null && legendVals.effectiveBuyingPrice !== legendVals.buyingPrice && (
                            <span className="text-fg-subtle">{t('chart.toolbar.crosshair.effBuy')} <span className="text-fg">{fmtHover(legendVals.effectiveBuyingPrice)}</span></span>
                        )}
                    </>
                ) : (
                    <span className="text-fg-subtle">{t('chart.legend.price')} <span className="text-fg">{fmtHover(legendVals.close)}</span></span>
                )}
                {legendVals.bulletinPrice != null && (
                    <span className="text-fg-subtle">{t('chart.toolbar.crosshair.bulletin')} <span className="text-fg">{fmtHover(legendVals.bulletinPrice)}</span></span>
                )}
                {legendVals.changePercent != null && (
                    <span className={legendVals.changePercent >= 0 ? 'text-success' : 'text-danger'}>
                        {legendVals.changePercent >= 0 ? '+' : ''}{legendVals.changePercent.toFixed(visibleDecimals(legendVals.changePercent, 2))}%
                    </span>
                )}
                {legendOverlayInds.map((ind) => (
                    <span key={ind.id} style={{ color: ind.color }}>{ind.type}{ind.period} {fmtHover(legendVals.overlays[ind.id])}</span>
                ))}
                {hasRSI && subValues?.rsi != null && (
                    <span style={{ color: rsiIndicator?.color || '#e91e63' }}>RSI {Number(subValues.rsi).toFixed(2)}</span>
                )}
                {hasMACD && subValues?.macd != null && (
                    // Colour each MACD value with its own line colour (MACD line vs signal) so the
                    // readout matches the sub-chart — signal '#f59e0b' mirrors useSubCharts.
                    <span className="text-fg-subtle">MACD <span style={{ color: macdIndicator?.color || '#06b6d4' }}>{Number(subValues.macd).toFixed(2)}</span>{subValues.signal != null && <> · <span style={{ color: '#f59e0b' }}>{Number(subValues.signal).toFixed(2)}</span></>}</span>
                )}
            </div>
        </div>
    );
};

export default ChartHoverLegend;
