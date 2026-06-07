import React from 'react';
import { useTranslation } from 'react-i18next';
import { BarChart2, X, Activity } from 'lucide-react';

const ChartSubPanels = ({
    hasRSI,
    rsiIndicator,
    rsiContainerRef,
    hasMACD,
    macdIndicator,
    macdContainerRef,
    indicators,
    toggleIndicator,
    showVolume,
    setShowVolume,
    volumeContainerRef,
    isFund,
    showInvestorCount,
    setShowInvestorCount,
    investorCountContainerRef,
    showPortfolioSize,
    setShowPortfolioSize,
    portfolioSizeContainerRef,
    subValues = {},
}) => {
    const { t } = useTranslation();
    return (
        <>
            {hasRSI && (
                <div className="border-t border-border-default flex-shrink-0">
                    <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                        <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                            <Activity className="w-3.5 h-3.5" style={{ color: rsiIndicator?.color || '#e91e63' }} />
                            RSI {rsiIndicator?.period || 14}
                            {subValues.rsi != null && (
                                <span className="ml-1 font-mono tabular-nums" style={{ color: rsiIndicator?.color || '#e91e63' }}>
                                    {subValues.rsi.toFixed(2)}
                                </span>
                            )}
                        </span>
                        <button
                            onClick={() => { const rsi = indicators.find(i => i.type === 'RSI'); if (rsi) toggleIndicator(rsi.id); }}
                            className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                        >
                            <X className="w-3.5 h-3.5" />
                        </button>
                    </div>
                    <div ref={rsiContainerRef} />
                </div>
            )}
            {hasMACD && (
                <div className="border-t border-border-default flex-shrink-0">
                    <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                        <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                            <Activity className="w-3.5 h-3.5" style={{ color: macdIndicator?.color || '#06b6d4' }} />
                            MACD (12, 26, 9)
                            {subValues.macd != null && (
                                <span className="ml-1 font-mono tabular-nums" style={{ color: macdIndicator?.color || '#06b6d4' }}>
                                    {subValues.macd.toFixed(4)}
                                </span>
                            )}
                            {subValues.signal != null && (
                                <span className="ml-1 font-mono tabular-nums" style={{ color: '#f59e0b' }}>
                                    {subValues.signal.toFixed(4)}
                                </span>
                            )}
                        </span>
                        <button
                            onClick={() => { const m = indicators.find(i => i.type === 'MACD'); if (m) toggleIndicator(m.id); }}
                            className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                        >
                            <X className="w-3.5 h-3.5" />
                        </button>
                    </div>
                    <div ref={macdContainerRef} />
                </div>
            )}
            {showVolume && (
                <div className="border-t border-border-default flex-shrink-0">
                    <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                        <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                            <BarChart2 className="w-3.5 h-3.5 text-emerald-400" />
                            {t('chart.volume')}
                        </span>
                        <button
                            onClick={() => setShowVolume(false)}
                            className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                        >
                            <X className="w-3.5 h-3.5" />
                        </button>
                    </div>
                    <div ref={volumeContainerRef} />
                </div>
            )}
            {isFund && showInvestorCount && (
                <div className="border-t border-border-default flex-shrink-0">
                    <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                        <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                            <Activity className="w-3.5 h-3.5 text-indigo-400" />
                            {t('lightweightChart.investorCount')}
                        </span>
                        <button
                            onClick={() => setShowInvestorCount(false)}
                            className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                        >
                            <X className="w-3.5 h-3.5" />
                        </button>
                    </div>
                    <div ref={investorCountContainerRef} />
                </div>
            )}
            {isFund && showPortfolioSize && (
                <div className="border-t border-border-default flex-shrink-0">
                    <div className="flex items-center justify-between px-3 py-1.5 bg-surface/40">
                        <span className="flex items-center gap-1.5 text-xs text-fg-muted font-medium">
                            <BarChart2 className="w-3.5 h-3.5 text-emerald-500" />
                            {t('lightweightChart.portfolioSize')}
                        </span>
                        <button
                            onClick={() => setShowPortfolioSize(false)}
                            className="p-0.5 rounded hover:bg-surface text-fg-subtle hover:text-fg transition-colors cursor-pointer bg-transparent border-none"
                        >
                            <X className="w-3.5 h-3.5" />
                        </button>
                    </div>
                    <div ref={portfolioSizeContainerRef} />
                </div>
            )}
        </>
    );
};

export default ChartSubPanels;
