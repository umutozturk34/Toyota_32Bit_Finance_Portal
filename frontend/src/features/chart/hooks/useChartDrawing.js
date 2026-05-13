import { useEffect, useRef, useCallback } from 'react';
import useDrawingRenderer from './useDrawingRenderer';
import useDrawingInteraction from './useDrawingInteraction';

const useChartDrawing = ({
    chartRef, candleSeriesRef, candleDataRef,
    isDark,
    drawings, addDrawing, cancelTool,
    fibTools, addFibTool, cancelFibTool,
    activeTool, activeFibTool,
    magnetMode,
    selectedIcon, iconSize,
    data, symbol,
    renderDrawingsRef,
    selectTool, selectFibTool,
    highlight,
}) => {
    const canvasOverlayRef = useRef(null);

    const rawPixelToChart = useCallback((x, y) => {
        const chart = chartRef.current;
        if (!chart) return null;
        try {
            const time = chart.timeScale().coordinateToTime(x);
            const price = candleSeriesRef.current?.coordinateToPrice(y);
            if (time && price !== null) return { time, price };
        } catch { /* chart not ready */ }
        return null;
    }, []);

    const chartCoordsToPixel = useCallback((time, price) => {
        const chart = chartRef.current;
        if (!chart || !candleSeriesRef.current) return null;
        try {
            const x = chart.timeScale().timeToCoordinate(time);
            const y = candleSeriesRef.current.priceToCoordinate(price);
            if (x !== null && y !== null) return { x, y };
        } catch { /* chart not ready */ }
        return null;
    }, []);

    const interaction = useDrawingInteraction({
        canvasOverlayRef,
        chartRef,
        isDark,
        drawings, addDrawing, cancelTool,
        fibTools, addFibTool, cancelFibTool,
        activeTool, activeFibTool,
        magnetMode,
        selectedIcon, iconSize,
        candleDataRef, data, symbol,
        renderDrawingsRef,
        selectTool, selectFibTool,
        rawPixelToChart, chartCoordsToPixel,
    });

    const { renderDrawings } = useDrawingRenderer({
        canvasOverlayRef,
        chartRef, candleSeriesRef,
        isDark,
        drawings, fibTools,
        isDrawing: interaction.isDrawing,
        startPoint: interaction.startPoint,
        currentPoint: interaction.currentPoint,
        activeTool, activeFibTool,
        drawClicksRef: interaction.drawClicksRef,
        magnetManagerRef: interaction.magnetManagerRef,
        pixelToChartCoords: interaction.pixelToChartCoords,
        chartCoordsToPixel,
        highlight,
    });

    useEffect(() => {
        renderDrawingsRef.current = renderDrawings;
    });

    useEffect(() => { renderDrawings(); }, [renderDrawings]);

    useEffect(() => {
        const shouldAnimate = interaction.isAnyToolActive || interaction.isDrawing;
        if (!shouldAnimate && (drawings.length === 0 && fibTools.length === 0)) return;
        if (!shouldAnimate) {
            renderDrawings();
            return;
        }
        let id;
        const animate = () => { renderDrawings(); id = requestAnimationFrame(animate); };
        id = requestAnimationFrame(animate);
        return () => cancelAnimationFrame(id);
    }, [interaction.isAnyToolActive, interaction.isDrawing, drawings, fibTools, renderDrawings]);

    return {
        canvasOverlayRef,
        freehandCanvasRef: interaction.freehandCanvasRef,
        handleMouseDown: interaction.handleMouseDown,
        handleMouseMove: interaction.handleMouseMove,
        handleMouseUp: interaction.handleMouseUp,
        handleMouseLeave: interaction.handleMouseLeave,
        isAnyToolActive: interaction.isAnyToolActive,
        handleSelectTool: interaction.handleSelectTool,
        handleSelectFibTool: interaction.handleSelectFibTool,
        cancelAllDrawing: interaction.cancelAllDrawing,
        textEditState: interaction.textEditState,
        commitTextEdit: interaction.commitTextEdit,
        cancelTextEdit: interaction.cancelTextEdit,
    };
};

export default useChartDrawing;
