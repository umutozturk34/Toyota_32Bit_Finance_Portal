import React, { useMemo, useRef, useEffect, useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../../shared/context/useTheme';
import useAppStore from '../../../shared/stores/useAppStore';
import useChartConfig from '../hooks/useChartConfig';
import useIndicators from '../hooks/useIndicators';
import useDrawings from '../hooks/useDrawings';
import useFibonacci from '../hooks/useFibonacci';
import useChartCore from '../hooks/useChartCore';
import useSubCharts from '../hooks/useSubCharts';
import useChartDrawing from '../hooks/useChartDrawing';
import useFullscreenMode from '../hooks/useFullscreenMode';
import useHighlightAnimation from '../hooks/useHighlightAnimation';
import ChartToolbar from './ChartToolbar';
import ChartToolRail from './ChartToolRail';
import ChartSidebar from './ChartSidebar';
import ChartSubPanels from './ChartSubPanels';
import ChartTextEditInput from './ChartTextEditInput';
import ChartHoverLegend from './lightweight/ChartHoverLegend';
import ChartLensStrip from './lightweight/ChartLensStrip';
import ChartLensPanel from './lightweight/ChartLensPanel';
import ChartTimeRangeBar from './lightweight/ChartTimeRangeBar';
import ChartEmptyState from './lightweight/ChartEmptyState';
import ChartTourAnchors from './lightweight/ChartTourAnchors';
import { DEFAULT_DRAWING_COLOR } from '../lib/drawingTools';
import { TABS, TIME_RANGES_FULL, TIME_RANGES_VIOP } from '../lib/chartConstants';
import Card from '../../../shared/components/card';

const LightweightChart = ({ data, symbol, assetType = 'CRYPTO', compareDatas = [], timeRange = '1Y', onTimeRangeChange, showSecondaryLines = true, onToggleSecondaryLines, sidebar = null }) => {
    const compareSymbol = compareDatas.length > 0 ? compareDatas.map(c => c.symbol).join(',') : null;
    const TIME_RANGES = assetType === 'VIOP' ? TIME_RANGES_VIOP : TIME_RANGES_FULL;
    const { i18n } = useTranslation();
    const { isDark } = useTheme();
    const renderDrawingsRef = useRef(null);
    const textDoneRef = useRef(false);
    const { isFullscreen, toggleFullscreen, wrapperRef } = useFullscreenMode();
    const { highlight, highlightDrawing, highlightFib } = useHighlightAnimation(renderDrawingsRef);
    // The whole Lens (top summary strip + right analytics panel) is collapsible — CLOSED by default so the chart
    // leads, while the always-on top-left legend covers OHLC + indicators. Open it for the full analytics cockpit.
    const [lensOpen, setLensOpen] = useState(false);

    const isFund = assetType === 'FUND';
    const isForex = assetType === 'FOREX';
    const isViop = assetType === 'VIOP';
    const showVolumeToggle = !isFund && !isForex && !isViop;
    const showFibTab = !isFund;
    const allowCandle = !isFund && !isForex && !isViop;

    const sidebarOpen = useAppStore((s) => s.chartSidebarOpen);
    const setSidebarOpen = useAppStore((s) => s.setChartSidebarOpen);
    const displayCurrency = useAppStore((s) => s.displayCurrency) || 'TRY';
    const activeTab = useAppStore((s) => s.chartActiveTab);
    const setActiveTab = useAppStore((s) => s.setChartActiveTab);

    const { config, setField } = useChartConfig(assetType, symbol, timeRange, !compareSymbol);
    const showVolume = config?.showVolume ?? false;
    const chartType = config?.chartType ?? ((isFund || isForex || isViop) ? 'line' : 'candle');
    const magnetMode = (['off', 'weak', 'strong'].includes(config?.magnetMode)) ? config.magnetMode : 'off';
    const selectedIcon = config?.selectedIcon ?? '\u{1F680}';
    const iconSize = config?.iconSize ?? 22;
    const showInvestorCount = config?.showInvestorCount ?? false;
    const showPortfolioSize = config?.showPortfolioSize ?? false;
    const drawingColor = config?.drawingColor ?? DEFAULT_DRAWING_COLOR;

    const setShowVolume = useCallback((v) => setField('showVolume', typeof v === 'function' ? v(showVolume) : v), [setField, showVolume]);
    const setChartType = useCallback((v) => setField('chartType', v), [setField]);
    const setMagnetMode = useCallback((v) => setField('magnetMode', v), [setField]);
    const setSelectedIcon = useCallback((v) => setField('selectedIcon', v), [setField]);
    const setIconSize = useCallback((v) => setField('iconSize', typeof v === 'function' ? v(iconSize) : v), [setField, iconSize]);
    const setDrawingColor = useCallback((v) => setField('drawingColor', v), [setField]);
    const setShowInvestorCount = useCallback((v) => setField('showInvestorCount', typeof v === 'function' ? v(showInvestorCount) : v), [setField, showInvestorCount]);
    const setShowPortfolioSize = useCallback((v) => setField('showPortfolioSize', typeof v === 'function' ? v(showPortfolioSize) : v), [setField, showPortfolioSize]);

    const hasInvestorCountData = useMemo(() =>
        isFund && data?.candles?.some(c => c.investorCount != null && Number(c.investorCount) > 0), [data, isFund]);
    const hasPortfolioSizeData = useMemo(() =>
        isFund && data?.candles?.some(c => c.portfolioSize != null && Number(c.portfolioSize) > 0), [data, isFund]);

    const { indicators, addIndicator, removeIndicator, updateIndicator, toggleIndicator } = useIndicators(assetType, symbol, timeRange, !compareSymbol);
    const { drawings, activeTool, addDrawing, removeDrawing, updateDrawing, undoDrawing, clearDrawings, selectTool, cancelTool } = useDrawings(assetType, symbol, timeRange, !compareSymbol);
    const { fibTools, activeFibTool, addFibTool, removeFibTool, clearFibTools, selectFibTool, cancelFibTool } = useFibonacci(assetType, symbol, timeRange, !compareSymbol);

    const filteredIndicators = useMemo(() => {
        if (isFund) return indicators.filter(i => i.type === 'SMA' || i.type === 'EMA');
        return indicators;
    }, [indicators, isFund]);

    const hasRSI = useMemo(() => !isFund && indicators.some(i => i.type === 'RSI' && i.visible), [indicators, isFund]);
    const rsiIndicator = useMemo(() => indicators.find(i => i.type === 'RSI' && i.visible), [indicators]);
    const hasMACD = useMemo(() => !isFund && indicators.some(i => i.type === 'MACD' && i.visible), [indicators, isFund]);
    const macdIndicator = useMemo(() => indicators.find(i => i.type === 'MACD' && i.visible), [indicators]);

    const { chartRef, chartContainerRef, candleSeriesRef, candleDataRef, volumeDataRef, trend, crosshairData, overlayLast, resetView, selectDate } = useChartCore({
        data: data, symbol, chartType: allowCandle ? chartType : 'line', isDark, indicators: filteredIndicators, renderDrawingsRef, assetType,
        compareDatas, timeRange, showSecondaryLines,
    });

    // Toolbar date picker: the calendar icon opens a native date picker bounded to the loaded data range;
    // choosing a date pins that day (LENS + analytics panel + legend + crosshair jump to it). Reset clears it.
    const dataDateBounds = useMemo(() => {
        const cs = data?.candles;
        if (!cs?.length) return { min: '', max: '' };
        const iso = (c) => String(c.candleDate || c.date || '').slice(0, 10);
        return { min: iso(cs[0]), max: iso(cs[cs.length - 1]) };
    }, [data]);
    const pickedDateValue = useMemo(() => {
        const cs = data?.candles;
        if (!cs?.length) return '';
        const c = (crosshairData?.index != null && cs[crosshairData.index]) ? cs[crosshairData.index] : cs[cs.length - 1];
        return String(c.candleDate || c.date || '').slice(0, 10);
    }, [crosshairData, data]);

    const { rsiContainerRef, macdContainerRef, volumeContainerRef, investorCountContainerRef, portfolioSizeContainerRef, subValues } = useSubCharts({
        chartRef, candleDataRef, volumeDataRef, isDark,
        hasRSI, rsiIndicator, hasMACD, macdIndicator, showVolume: showVolumeToggle && showVolume, data: data,
        showInvestorCount: isFund && showInvestorCount,
        showPortfolioSize: isFund && showPortfolioSize,
        isFullscreen,
    });

    const {
        canvasOverlayRef, freehandCanvasRef,
        handleMouseDown, handleMouseMove, handleMouseUp, handleMouseLeave,
        isAnyToolActive, handleSelectTool, handleSelectFibTool, cancelAllDrawing,
        textEditState, commitTextEdit, cancelTextEdit,
    } = useChartDrawing({
        chartRef, candleSeriesRef, candleDataRef, isDark,
        drawings, addDrawing, cancelTool,
        fibTools: showFibTab ? fibTools : [], addFibTool, cancelFibTool,
        activeTool, activeFibTool: showFibTab ? activeFibTool : null,
        magnetMode, selectedIcon, iconSize, drawingColor,
        data: data, symbol, renderDrawingsRef,
        selectTool, selectFibTool,
        highlight,
    });

    useEffect(() => {
        if (textEditState) textDoneRef.current = false;
    }, [textEditState]);

    if (!data?.candles?.length) {
        return <ChartEmptyState />;
    }

    // On-chart hover legend (TradingView-style): the hovered day's OHLC/price + every OPEN overlay indicator's
    // value at that point + open sub-charts' (RSI/MACD) values. Shown only while a crosshair is active so the
    // chart stays clean when idle.
    const hoverLocale = i18n.language?.startsWith('tr') ? 'tr-TR' : 'en-US';
    const hoverSym = { TRY: '₺', USD: '$', EUR: '€' }[displayCurrency] || '';
    const fmtHover = (v) => {
      if (v == null) return '—';
      const n = Number(v);
      // Astronomical (hyperinflation-nominal) values would balloon the legend over the chart; compact them
      // like the axis. Small prices keep full 4/2-decimal precision — the whole point of the readout.
      if (Math.abs(n) >= 1_000_000) return `${hoverSym}${n.toLocaleString(hoverLocale, { notation: 'compact', maximumFractionDigits: 2 })}`;
      return `${hoverSym}${n.toLocaleString(hoverLocale, { maximumFractionDigits: Math.abs(n) < 10 ? 4 : 2 })}`;
    };
    // The legend reads the hovered candle, else falls back to the latest — so OHLC + indicators are ALWAYS shown.
    const lastCandle = data?.candles?.length ? data.candles[data.candles.length - 1] : null;
    const legendCandle = crosshairData?.index != null ? data?.candles?.[crosshairData.index] : lastCandle;
    const legendDate = legendCandle ? new Date(legendCandle.candleDate || legendCandle.date).toLocaleDateString(hoverLocale, { day: '2-digit', month: 'short', year: 'numeric' }) : null;
    const legendVals = crosshairData || (lastCandle ? {
        open: Number(lastCandle.open ?? lastCandle.close ?? lastCandle.price ?? 0),
        high: Number(lastCandle.high ?? lastCandle.close ?? lastCandle.price ?? 0),
        low: Number(lastCandle.low ?? lastCandle.close ?? lastCandle.price ?? 0),
        close: Number(lastCandle.close ?? lastCandle.price ?? lastCandle.sellingPrice ?? 0),
        // Forex legs + fund bulletin price so the IDLE legend (latest candle) shows the same per-type prices the
        // Lens does — not just on hover. null when absent so the per-field render guards below stay off.
        sellingPrice: lastCandle.sellingPrice != null ? Number(lastCandle.sellingPrice) : null,
        buyingPrice: lastCandle.buyingPrice != null ? Number(lastCandle.buyingPrice) : null,
        effectiveSellingPrice: lastCandle.effectiveSellingPrice != null ? Number(lastCandle.effectiveSellingPrice) : null,
        effectiveBuyingPrice: lastCandle.effectiveBuyingPrice != null ? Number(lastCandle.effectiveBuyingPrice) : null,
        bulletinPrice: lastCandle.bulletinPrice != null ? Number(lastCandle.bulletinPrice) : null,
        changePercent: null,
        overlays: overlayLast,
    } : null);
    const legendOverlayInds = filteredIndicators.filter((i) => i.visible && (i.type === 'SMA' || i.type === 'EMA') && legendVals?.overlays?.[i.id] != null);
    // Forex shows its bid/ask + banknote (effective) legs in place of the generic price; funds add bulletin price.
    // Field-presence driven (mirrors DataWindowPanel) so no per-asset-type branching leaks into the view.
    const legendHasForexLegs = legendVals?.sellingPrice != null && legendVals?.buyingPrice != null;

    return (
        <div className="space-y-2 sm:space-y-3">
        {data?.candles?.length > 0 && !isFullscreen && (
            <ChartLensStrip
                lensOpen={lensOpen}
                setLensOpen={setLensOpen}
                candles={data.candles}
                crosshairData={crosshairData}
                assetType={assetType}
            />
        )}
        <Card ref={wrapperRef} variant="elevated" radius="xl" padding="none" backdropBlur interactive={false} className={`flex flex-col lg:flex-row !overflow-x-hidden !overflow-y-visible ${isFullscreen ? 'h-[100dvh] !rounded-none !overflow-y-auto' : 'min-h-[300px] sm:min-h-[380px] lg:min-h-[480px]'}`}>
            <ChartTourAnchors setSidebarOpen={setSidebarOpen} setActiveTab={setActiveTab} />
            <ChartToolRail
                activeTool={activeTool}
                activeFibTool={showFibTab ? activeFibTool : null}
                isAnyToolActive={isAnyToolActive}
                onSelectTool={handleSelectTool}
                onSelectFibTool={handleSelectFibTool}
                onCancelAll={cancelAllDrawing}
                showFib={showFibTab}
                drawingColor={drawingColor}
                setDrawingColor={setDrawingColor}
                selectedIcon={selectedIcon}
                setSelectedIcon={setSelectedIcon}
                iconSize={iconSize}
                setIconSize={setIconSize}
                drawingsCount={drawings.length}
                onUndo={undoDrawing}
                onClear={clearDrawings}
            />
            {sidebarOpen && (
                <ChartSidebar
                    tabs={TABS}
                    showFibTab={false}
                    activeTab={activeTab === 'drawings' ? 'drawings' : 'indicators'}
                    setActiveTab={setActiveTab}
                    isFund={isFund}
                    isForex={isForex}
                    showVolumeToggle={showVolumeToggle}
                    indicators={indicators}
                    addIndicator={addIndicator}
                    removeIndicator={removeIndicator}
                    updateIndicator={updateIndicator}
                    toggleIndicator={toggleIndicator}
                    activeTool={activeTool}
                    handleSelectTool={handleSelectTool}
                    cancelTool={cancelTool}
                    drawings={drawings}
                    removeDrawing={removeDrawing}
                    updateDrawing={updateDrawing}
                    undoDrawing={undoDrawing}
                    clearDrawings={clearDrawings}
                    selectedIcon={selectedIcon}
                    setSelectedIcon={setSelectedIcon}
                    iconSize={iconSize}
                    setIconSize={setIconSize}
                    highlightDrawing={highlightDrawing}
                    activeFibTool={activeFibTool}
                    handleSelectFibTool={handleSelectFibTool}
                    cancelFibTool={cancelFibTool}
                    fibTools={fibTools}
                    removeFibTool={removeFibTool}
                    clearFibTools={clearFibTools}
                    highlightFib={highlightFib}
                    showSecondaryLines={showSecondaryLines}
                    onToggleSecondaryLines={onToggleSecondaryLines}
                    showVolume={showVolume}
                    setShowVolume={setShowVolume}
                    hasInvestorCountData={hasInvestorCountData}
                    showInvestorCount={showInvestorCount}
                    setShowInvestorCount={setShowInvestorCount}
                    hasPortfolioSizeData={hasPortfolioSizeData}
                    showPortfolioSize={showPortfolioSize}
                    setShowPortfolioSize={setShowPortfolioSize}
                />
            )}
            <div className={`relative flex flex-1 min-w-0 flex-col ${isFullscreen ? 'xl:flex-row' : 'xl:block'}`}>
            <div className={`flex-1 flex flex-col min-w-0 ${isFullscreen ? '' : (lensOpen ? 'xl:mr-[340px]' : '')}`}>
                <ChartToolbar
                    isDark={isDark}
                    sidebarOpen={sidebarOpen}
                    setSidebarOpen={setSidebarOpen}
                    chartType={allowCandle ? chartType : 'line'}
                    setChartType={allowCandle ? setChartType : () => {}}
                    magnetMode={magnetMode}
                    setMagnetMode={setMagnetMode}
                    symbol={symbol}
                    trend={trend}
                    crosshairData={crosshairData}
                    overlayLast={overlayLast}
                    assetType={assetType}
                    indicators={filteredIndicators}
                    drawings={drawings}
                    isAnyToolActive={isAnyToolActive}
                    activeTool={activeTool}
                    activeFibTool={showFibTab ? activeFibTool : null}
                    cancelAllDrawing={cancelAllDrawing}
                    allowCandle={allowCandle}
                    compareSymbol={compareSymbol}
                    isFullscreen={isFullscreen}
                    onToggleFullscreen={toggleFullscreen}
                    onResetView={resetView}
                />
                <ChartTimeRangeBar
                    pickedDateValue={pickedDateValue}
                    dataDateBounds={dataDateBounds}
                    selectDate={selectDate}
                    timeRanges={TIME_RANGES}
                    timeRange={timeRange}
                    onTimeRangeChange={onTimeRangeChange}
                />
                {/* overflow-hidden: clip the chart canvas to its box so a stale fullscreen-height canvas can't
                    inflate the container (or spill over the page) before the resize settles it back down. */}
                <div className={`relative flex-1 overflow-hidden ${isFullscreen
                    ? 'min-h-0'
                    : (hasRSI || hasMACD || (showVolumeToggle && showVolume) || (isFund && (showInvestorCount || showPortfolioSize))
                        // Sub-charts (RSI/MACD/volume…) take some of the MAIN chart's height instead of only stacking
                        // below it, so opening them doesn't push the page far down — the user barely has to scroll.
                        ? 'min-h-[30dvh] sm:min-h-[250px] lg:min-h-[265px] xl:min-h-[300px]'
                        : 'min-h-[40dvh] sm:min-h-[330px] lg:min-h-[350px] xl:min-h-[440px]')}`}>
                    {/* Absolutely positioned, NOT in-flow w-full/h-full. Lightweight-charts sizes this div's
                        canvas via applyOptions(height); handleResize then reads it back from clientHeight. If
                        the div were in-flow with h-full inside this content-sized (min-h, not fixed-height)
                        flex parent, the canvas height would feed the parent's height — so after a fullscreen
                        EXIT clientHeight read the stale fullscreen height and re-applied it, leaving the chart
                        stuck tall and spilling down the page. Anchored to the relative, flex-sized chart area,
                        the container's size is driven by the layout and never by the canvas, breaking the loop. */}
                    <div ref={chartContainerRef} className="absolute inset-0" />
                    <canvas
                        ref={canvasOverlayRef}
                        className="absolute inset-0 w-full h-full"
                        style={{
                            cursor: isAnyToolActive ? 'crosshair' : 'default',
                            zIndex: isAnyToolActive ? 10 : 1,
                            pointerEvents: isAnyToolActive ? 'auto' : 'none',
                            touchAction: isAnyToolActive ? 'none' : 'auto',
                            userSelect: 'none',
                            WebkitUserSelect: 'none',
                            WebkitTouchCallout: 'none',
                        }}
                        onMouseDown={handleMouseDown}
                        onMouseMove={handleMouseMove}
                        onMouseUp={handleMouseUp}
                        onMouseLeave={handleMouseLeave}
                        onTouchStart={handleMouseDown}
                        onTouchMove={handleMouseMove}
                        onTouchEnd={handleMouseUp}
                        onTouchCancel={handleMouseLeave}
                    />
                    <canvas
                        ref={freehandCanvasRef}
                        className="absolute inset-0 w-full h-full pointer-events-none"
                        style={{ zIndex: 11 }}
                    />
                    <ChartHoverLegend
                        legendVals={legendVals}
                        legendDate={legendDate}
                        fmtHover={fmtHover}
                        allowCandle={allowCandle}
                        chartType={chartType}
                        legendHasForexLegs={legendHasForexLegs}
                        legendOverlayInds={legendOverlayInds}
                        hasRSI={hasRSI}
                        subValues={subValues}
                        rsiIndicator={rsiIndicator}
                        hasMACD={hasMACD}
                        macdIndicator={macdIndicator}
                    />
                    <ChartTextEditInput
                        textEditState={textEditState}
                        isDark={isDark}
                        textDoneRef={textDoneRef}
                        commitTextEdit={commitTextEdit}
                        cancelTextEdit={cancelTextEdit}
                    />
                </div>
                <ChartSubPanels
                    hasRSI={hasRSI}
                    rsiIndicator={rsiIndicator}
                    rsiContainerRef={rsiContainerRef}
                    hasMACD={hasMACD}
                    macdIndicator={macdIndicator}
                    macdContainerRef={macdContainerRef}
                    indicators={indicators}
                    toggleIndicator={toggleIndicator}
                    showVolume={showVolume}
                    setShowVolume={setShowVolume}
                    volumeContainerRef={volumeContainerRef}
                    isFund={isFund}
                    showInvestorCount={showInvestorCount}
                    setShowInvestorCount={setShowInvestorCount}
                    investorCountContainerRef={investorCountContainerRef}
                    showPortfolioSize={showPortfolioSize}
                    setShowPortfolioSize={setShowPortfolioSize}
                    portfolioSizeContainerRef={portfolioSizeContainerRef}
                    subValues={subValues}
                />

            </div>
            {lensOpen && (data?.candles?.length > 0 || sidebar) && (
                <ChartLensPanel
                    candles={data.candles}
                    crosshairData={crosshairData}
                    assetType={assetType}
                    sidebar={sidebar}
                    isFullscreen={isFullscreen}
                />
            )}
            </div>
        </Card>
        </div>
    );
};

export default LightweightChart;
