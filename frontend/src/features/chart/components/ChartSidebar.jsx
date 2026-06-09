import React from 'react';
import { useTranslation } from 'react-i18next';
import { BarChart2, Activity } from 'lucide-react';
import IndicatorPanel from './IndicatorPanel';
import DrawingPanel from './DrawingPanel';
import FibonacciPanel from './FibonacciPanel';

const ChartSidebar = ({
    tabs,
    showFibTab,
    activeTab,
    setActiveTab,
    isFund,
    isForex,
    showVolumeToggle,
    indicators,
    addIndicator,
    removeIndicator,
    updateIndicator,
    toggleIndicator,
    activeTool,
    handleSelectTool,
    cancelTool,
    drawings,
    removeDrawing,
    undoDrawing,
    clearDrawings,
    selectedIcon,
    setSelectedIcon,
    iconSize,
    setIconSize,
    highlightDrawing,
    activeFibTool,
    handleSelectFibTool,
    cancelFibTool,
    fibTools,
    removeFibTool,
    clearFibTools,
    highlightFib,
    showSecondaryLines,
    onToggleSecondaryLines,
    showVolume,
    setShowVolume,
    hasInvestorCountData,
    showInvestorCount,
    setShowInvestorCount,
    hasPortfolioSizeData,
    showPortfolioSize,
    setShowPortfolioSize,
}) => {
    const { t } = useTranslation();
    // Height caps are mobile-only (max-lg): they bound the panel so the chart keeps room when it stacks on top
    // in the column layout. They MUST be scoped to max-lg — a desktop is always landscape, so a bare
    // `landscape:max-h` leaked onto large screens and capped the panel at ~80vh, leaving it short of the chart
    // in fullscreen. At lg+ there is no max-h, so flex align-stretch grows it to the chart's full height
    // (normal AND fullscreen) dynamically.
    return (
        <div className="w-full lg:w-60 shrink-0 border-b lg:border-b-0 lg:border-r border-border-default flex flex-col bg-surface/40 backdrop-blur-md relative z-20 min-h-[200px] max-lg:max-h-[55dvh] max-lg:landscape:max-h-[85dvh] sm:max-lg:max-h-[50dvh] sm:max-lg:landscape:max-h-[80dvh] lg:min-h-0">
            <div className="pointer-events-none absolute inset-y-0 left-0 w-px bg-gradient-to-b from-indigo-400/40 via-fuchsia-400/20 to-transparent" />
            <div className="pointer-events-none absolute top-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-indigo-400/20 to-transparent" />
            <div className="flex border-b border-border-default">
                {tabs.filter(tab => showFibTab || tab.id !== 'fibonacci').map(({ id, labelKey, Icon }) => {
                    const isActive = activeTab === id;
                    return (
                        <button
                            key={id}
                            onClick={() => setActiveTab(id)}
                            className={`relative flex-1 flex flex-col items-center gap-1 py-3 px-1 text-[9px] font-semibold uppercase tracking-[0.04em] border-none cursor-pointer transition-all duration-200 bg-transparent hover:bg-surface/60 min-w-0 ${isActive ? 'text-fg' : 'text-fg-muted hover:text-fg'}`}
                        >
                            <Icon className={`w-4 h-4 transition-all ${isActive ? 'text-indigo-400 drop-shadow-[0_0_6px_rgba(99,102,241,0.5)]' : ''}`} />
                            {t(labelKey)}
                            {isActive && (
                                <span className="absolute bottom-0 left-2 right-2 h-[2px] rounded-full bg-gradient-to-r from-indigo-400 via-fuchsia-400 to-indigo-400 shadow-[0_0_8px_rgba(99,102,241,0.6)]" />
                            )}
                        </button>
                    );
                })}
            </div>
            <div className="flex-1 overflow-y-auto p-3 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar-thumb]:bg-border-default [&::-webkit-scrollbar-thumb]:rounded-full hover:[&::-webkit-scrollbar-thumb]:bg-border-hover" style={{ scrollbarWidth: 'thin' }}>
                {activeTab === 'indicators' && (
                    <IndicatorPanel
                        indicators={indicators}
                        addIndicator={addIndicator}
                        removeIndicator={removeIndicator}
                        updateIndicator={updateIndicator}
                        toggleIndicator={toggleIndicator}
                        allowedTypes={isFund ? ['SMA', 'EMA'] : undefined}
                    />
                )}
                {activeTab === 'drawings' && (
                    <DrawingPanel
                        activeTool={activeTool}
                        selectTool={handleSelectTool}
                        cancelTool={cancelTool}
                        drawings={drawings}
                        removeDrawing={removeDrawing}
                        undoDrawing={undoDrawing}
                        clearDrawings={clearDrawings}
                        selectedIcon={selectedIcon}
                        setSelectedIcon={setSelectedIcon}
                        iconSize={iconSize}
                        setIconSize={setIconSize}
                        onHighlight={highlightDrawing}
                    />
                )}
                {activeTab === 'fibonacci' && (
                    <FibonacciPanel
                        activeFibTool={activeFibTool}
                        selectFibTool={handleSelectFibTool}
                        cancelFibTool={cancelFibTool}
                        fibTools={fibTools}
                        removeFibTool={removeFibTool}
                        clearFibTools={clearFibTools}
                        onHighlight={highlightFib}
                    />
                )}
            </div>
            {(showVolumeToggle || isFund || isForex) && (
            <div className="border-t border-border-default px-3 pt-2.5 pb-3 space-y-1.5">
                <p className="text-[9px] font-bold uppercase tracking-[0.16em] text-fg-subtle pb-1">{t('lightweightChart.view')}</p>
                {(isFund || isForex) && onToggleSecondaryLines && (
                    <button
                        onClick={onToggleSecondaryLines}
                        className={`w-full flex items-center gap-2 px-2.5 py-2 min-h-[40px] rounded-lg text-xs font-medium border transition-all duration-200 cursor-pointer ${showSecondaryLines ? 'border-violet-400/40 bg-violet-400/10 text-violet-400 shadow-[0_0_12px_rgba(167,139,250,0.15)]' : 'border-border-default bg-transparent text-fg-muted hover:text-fg hover:border-border-hover'}`}
                        title={isFund ? t('lightweightChart.toggleBulletinPrice', { defaultValue: 'Borsa Fiyatı' }) : t('lightweightChart.toggleBuyingPrice', { defaultValue: 'Alış Fiyatı' })}
                    >
                        <BarChart2 className="w-3.5 h-3.5" />
                        {isFund ? t('lightweightChart.bulletinPriceToggle', { defaultValue: 'Borsa Fiyatı' }) : t('lightweightChart.buyingPriceToggle', { defaultValue: 'Alış Fiyatı' })}
                    </button>
                )}
                {showVolumeToggle && (
                    <button
                        onClick={() => setShowVolume(!showVolume)}
                        className={`w-full flex items-center gap-2 px-2.5 py-2 min-h-[40px] rounded-lg text-xs font-medium border transition-all duration-200 cursor-pointer ${showVolume ? 'border-emerald-400/40 bg-emerald-400/10 text-emerald-400 shadow-[0_0_12px_rgba(52,211,153,0.15)]' : 'border-border-default bg-transparent text-fg-muted hover:text-fg hover:border-border-hover'}`}
                    >
                        <BarChart2 className="w-3.5 h-3.5" />
                        {t('chart.volume')}
                    </button>
                )}
                {isFund && (
                    <>
                        <button
                            onClick={() => hasInvestorCountData && setShowInvestorCount(!showInvestorCount)}
                            disabled={!hasInvestorCountData}
                            className={`w-full flex items-center gap-2 px-2.5 py-2 min-h-[40px] rounded-lg text-xs font-medium border transition-all duration-200 ${!hasInvestorCountData ? 'opacity-45 cursor-not-allowed border-border-default text-fg-subtle' : showInvestorCount ? 'cursor-pointer border-indigo-400/40 bg-indigo-400/10 text-indigo-400 shadow-[0_0_12px_rgba(99,102,241,0.15)]' : 'cursor-pointer border-border-default text-fg-muted hover:text-fg hover:border-border-hover'}`}
                            title={hasInvestorCountData ? t('lightweightChart.investorCount') : t('lightweightChart.noInvestorCountData')}
                        >
                            <Activity className="w-3.5 h-3.5" />
                            {t('lightweightChart.investorCount')}
                        </button>
                        <button
                            onClick={() => hasPortfolioSizeData && setShowPortfolioSize(!showPortfolioSize)}
                            disabled={!hasPortfolioSizeData}
                            className={`w-full flex items-center gap-2 px-2.5 py-2 min-h-[40px] rounded-lg text-xs font-medium border transition-all duration-200 ${!hasPortfolioSizeData ? 'opacity-45 cursor-not-allowed border-border-default text-fg-subtle' : showPortfolioSize ? 'cursor-pointer border-emerald-500/40 bg-emerald-500/10 text-emerald-500 shadow-[0_0_12px_rgba(16,185,129,0.15)]' : 'cursor-pointer border-border-default text-fg-muted hover:text-fg hover:border-border-hover'}`}
                            title={hasPortfolioSizeData ? t('lightweightChart.portfolioSize') : t('lightweightChart.noPortfolioSizeData')}
                        >
                            <BarChart2 className="w-3.5 h-3.5" />
                            {t('lightweightChart.portfolioSize')}
                        </button>
                    </>
                )}
            </div>
            )}
        </div>
    );
};

export default ChartSidebar;
