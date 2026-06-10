import { useEffect, useRef, useState, useCallback } from 'react';
import { calculateFibRetracement, calculateFibExtension } from '../lib/fibonacci';
import { createMagnetManager } from '../lib/magnetManager';

const useDrawingInteraction = ({
    canvasOverlayRef,
    addDrawing, cancelTool,
    addFibTool, cancelFibTool,
    activeTool, activeFibTool,
    magnetMode,
    selectedIcon, iconSize,
    drawingColor = '#5E6AD2',
    candleDataRef, data, symbol,
    renderDrawingsRef,
    selectTool, selectFibTool,
    rawPixelToChart, chartCoordsToPixel,
}) => {
    const freehandCanvasRef = useRef(null);
    const freehandPointsRef = useRef([]);
    const drawClicksRef = useRef([]);
    const magnetManagerRef = useRef(null);
    const isDrawingRef = useRef(false);
    const startPointRef = useRef(null);
    const currentPointRef = useRef(null);
    const [isDrawing, setIsDrawing] = useState(false);
    const [startPoint, setStartPoint] = useState(null);
    const [currentPoint, setCurrentPoint] = useState(null);
    const [textEditState, setTextEditState] = useState(null);

    const isAnyToolActive = activeTool || activeFibTool;

    const pixelToChartCoords = useCallback((x, y) => {
        const mgr = magnetManagerRef.current;
        if (mgr) return mgr.snapImmediate(x, y);
        return rawPixelToChart(x, y);
    }, [rawPixelToChart]);

    const handleSelectTool = useCallback((tool) => {
        cancelFibTool();
        drawClicksRef.current = [];
        selectTool(tool);
    }, [selectTool, cancelFibTool]);

    const handleSelectFibTool = useCallback((type) => {
        cancelTool();
        selectFibTool(type);
    }, [selectFibTool, cancelTool]);

    const cancelAllDrawing = useCallback(() => {
        cancelTool();
        cancelFibTool();
        drawClicksRef.current = [];
    }, [cancelTool, cancelFibTool]);

    const commitTextEdit = useCallback((content) => {
        if (!textEditState) return;
        // TEXT glyph colour is intentionally NOT the active drawingColor: the renderer paints it theme-aware
        // (light glyph on dark / dark on light) so a label stays legible on its box regardless of the line
        // colour. Omitting `color` lets that themed fallback win — an arbitrary line colour (e.g. yellow on the
        // near-white light box) would be unreadable.
        addDrawing({
            type: 'TEXT',
            time: textEditState.time,
            price: textEditState.price,
            content: content || 'Text',
            priceFontHeight: textEditState.priceFontHeight,
        });
        setTextEditState(null);
    }, [textEditState, addDrawing]);

    const cancelTextEdit = useCallback(() => {
        setTextEditState(null);
    }, []);

    useEffect(() => {
        magnetManagerRef.current = createMagnetManager();
        const offSnap = magnetManagerRef.current.on('snap', () => { if (renderDrawingsRef.current) renderDrawingsRef.current(); });
        const offRelease = magnetManagerRef.current.on('release', () => { if (renderDrawingsRef.current) renderDrawingsRef.current(); });
        return () => {
            offSnap();
            offRelease();
            magnetManagerRef.current.destroy();
            magnetManagerRef.current = null;
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-once magnet manager create/destroy lifecycle; must not re-run on prop changes
    }, []);

    useEffect(() => {
        const mgr = magnetManagerRef.current;
        if (!mgr) return;
        mgr.setMode(magnetMode);
    }, [magnetMode]);

    useEffect(() => {
        const mgr = magnetManagerRef.current;
        if (!mgr) return;
        mgr.setToolActive(!!isAnyToolActive);
    }, [isAnyToolActive]);

    useEffect(() => {
        const mgr = magnetManagerRef.current;
        if (!mgr) return;
        mgr.setCandles(candleDataRef.current || []);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- candleDataRef read via ref intentionally; re-syncs only when data/symbol change
    }, [data, symbol]);

    useEffect(() => {
        const mgr = magnetManagerRef.current;
        if (!mgr) return;
        mgr.setConverters({ toPixel: chartCoordsToPixel, fromPixel: rawPixelToChart });
    }, [chartCoordsToPixel, rawPixelToChart]);

    const getMousePos = (e) => {
        const canvas = canvasOverlayRef.current;
        if (!canvas) return null;
        const rect = canvas.getBoundingClientRect();
        const point = e.touches?.[0] ?? e.changedTouches?.[0] ?? e;
        return { x: point.clientX - rect.left, y: point.clientY - rect.top };
    };

    const handleMouseDown = (e) => {
        if (!isAnyToolActive) return;
        // Touch scroll-vs-draw is actually prevented by the overlay's touch-action:none (React 19 delegates touch
        // listeners as passive, so preventDefault is a no-op there); guard on cancelable to avoid the WebKit/Blink
        // "Unable to preventDefault inside passive event listener" warning while still working for mouse.
        if (e.cancelable) e.preventDefault();
        const pos = getMousePos(e);
        if (!pos) return;

        if (activeFibTool) {
            drawClicksRef.current.push(pos);
            setCurrentPoint(pos);
            const neededClicks = activeFibTool === 'EXTENSION' ? 3 : 2;
            if (drawClicksRef.current.length >= neededClicks) {
                const clicks = drawClicksRef.current;
                const points = clicks.map(p => pixelToChartCoords(p.x, p.y)).filter(Boolean);
                if (points.length >= neededClicks) {
                    let levels;
                    if (activeFibTool === 'RETRACEMENT') {
                        levels = calculateFibRetracement(points[0].price, points[1].price);
                    } else {
                        levels = calculateFibExtension(points[0].price, points[1].price, points[2].price);
                    }
                    addFibTool({
                        type: activeFibTool,
                        startTime: points[0].time,
                        startPrice: points[0].price,
                        endTime: points[1].time,
                        endPrice: points[1].price,
                        levels,
                    });
                }
                drawClicksRef.current = [];
                cancelFibTool();
            }
            return;
        }

        if (activeTool === 'HORIZONTAL_LINE' || activeTool === 'VERTICAL_LINE') {
            const coords = pixelToChartCoords(pos.x, pos.y);
            if (coords) {
                addDrawing({
                    type: activeTool,
                    time: coords.time,
                    price: coords.price,
                    color: drawingColor,
                });
            }
            cancelTool();
            return;
        }

        if (activeTool === 'TEXT') {
            const coords = pixelToChartCoords(pos.x, pos.y);
            if (coords) {
                const rawAnchor = rawPixelToChart(pos.x, pos.y);
                const rawAbove = rawPixelToChart(pos.x, pos.y - 14);
                const priceFontHeight = (rawAnchor && rawAbove) ? Math.abs(rawAbove.price - rawAnchor.price) : 0;
                setTextEditState({ x: pos.x, y: pos.y, time: coords.time, price: coords.price, priceFontHeight });
            }
            return;
        }

        if (activeTool === 'ICON') {
            const coords = pixelToChartCoords(pos.x, pos.y);
            if (coords) {
                const sz = iconSize || 22;
                const rawAnchor = rawPixelToChart(pos.x, pos.y);
                const rawAbove = rawPixelToChart(pos.x, pos.y - sz);
                const priceFontHeight = (rawAnchor && rawAbove) ? Math.abs(rawAbove.price - rawAnchor.price) : 0;
                addDrawing({
                    type: 'ICON',
                    time: coords.time,
                    price: coords.price,
                    iconId: selectedIcon,
                    iconSize: sz,
                    priceFontHeight,
                });
            }
            cancelTool();
            return;
        }

        setIsDrawing(true);
        isDrawingRef.current = true;
        setStartPoint(pos);
        startPointRef.current = pos;
        setCurrentPoint(pos);
        currentPointRef.current = pos;

        if (activeTool === 'FREEHAND') {
            freehandPointsRef.current = [pos];
            const fc = freehandCanvasRef.current;
            if (fc) {
                const fctx = fc.getContext('2d');
                const rect = fc.getBoundingClientRect();
                const dpr = window.devicePixelRatio || 1;
                fc.width = rect.width * dpr;
                fc.height = rect.height * dpr;
                fctx.scale(dpr, dpr);
                fctx.clearRect(0, 0, rect.width, rect.height);
            }
        }
    };

    const handleMouseMove = (e) => {
        const pos = getMousePos(e);
        if (!pos) return;
        const mgr = magnetManagerRef.current;
        if (mgr) mgr.update(pos.x, pos.y);

        if (activeFibTool && drawClicksRef.current.length > 0) {
            setCurrentPoint(pos);
            currentPointRef.current = pos;
            return;
        }

        if (!isDrawingRef.current || !activeTool) return;
        setCurrentPoint(pos);
        currentPointRef.current = pos;

        if (activeTool === 'FREEHAND') {
            freehandPointsRef.current.push(pos);
            const fc = freehandCanvasRef.current;
            if (fc) {
                const fctx = fc.getContext('2d');
                fctx.strokeStyle = drawingColor;
                fctx.lineWidth = 2;
                fctx.lineCap = 'round';
                fctx.lineJoin = 'round';
                const pts = freehandPointsRef.current;
                if (pts.length > 1) {
                    fctx.beginPath();
                    fctx.moveTo(pts[pts.length - 2].x, pts[pts.length - 2].y);
                    fctx.lineTo(pts[pts.length - 1].x, pts[pts.length - 1].y);
                    fctx.stroke();
                }
            }
        }
    };

    const handleMouseUp = (e) => {
        if (!activeTool || !isDrawingRef.current) return;
        const pos = getMousePos(e);
        const sp = startPointRef.current;

        if (activeTool === 'FREEHAND') {
            const chartPoints = freehandPointsRef.current
                .map(p => { const c = pixelToChartCoords(p.x, p.y); return c ? { time: c.time, price: c.price } : null; })
                .filter(Boolean);
            if (chartPoints.length > 1) addDrawing({ type: 'FREEHAND', points: chartPoints, color: drawingColor });
            freehandPointsRef.current = [];
            const fc = freehandCanvasRef.current;
            if (fc) { const fctx = fc.getContext('2d'); fctx.clearRect(0, 0, fc.width, fc.height); }
        } else if (activeTool === 'TREND_LINE' && pos && sp) {
            const sc = pixelToChartCoords(sp.x, sp.y);
            const ec = pixelToChartCoords(pos.x, pos.y);
            if (sc && ec) {
                addDrawing({
                    type: 'TREND_LINE',
                    startTime: sc.time, startPrice: sc.price,
                    endTime: ec.time, endPrice: ec.price,
                    color: drawingColor,
                });
            }
        }

        setIsDrawing(false);
        isDrawingRef.current = false;
        setStartPoint(null);
        startPointRef.current = null;
        setCurrentPoint(null);
        currentPointRef.current = null;
        if (magnetManagerRef.current) magnetManagerRef.current.clear();
        if (activeTool !== 'FREEHAND') cancelTool();
    };

    const handleMouseLeave = () => {
        if (magnetManagerRef.current) magnetManagerRef.current.clear();
        if (isDrawingRef.current && activeTool === 'FREEHAND' && freehandPointsRef.current.length > 1) {
            const chartPoints = freehandPointsRef.current
                .map(p => { const c = pixelToChartCoords(p.x, p.y); return c ? { time: c.time, price: c.price } : null; })
                .filter(Boolean);
            if (chartPoints.length > 1) addDrawing({ type: 'FREEHAND', points: chartPoints, color: drawingColor });
            freehandPointsRef.current = [];
            const fc = freehandCanvasRef.current;
            if (fc) { const fctx = fc.getContext('2d'); fctx.clearRect(0, 0, fc.width, fc.height); }
        }
        setIsDrawing(false);
        isDrawingRef.current = false;
        setStartPoint(null);
        startPointRef.current = null;
        setCurrentPoint(null);
        currentPointRef.current = null;
    };

    return {
        freehandCanvasRef, drawClicksRef, magnetManagerRef,
        isDrawing, startPoint, currentPoint,
        isAnyToolActive, pixelToChartCoords,
        handleMouseDown, handleMouseMove, handleMouseUp, handleMouseLeave,
        handleSelectTool, handleSelectFibTool, cancelAllDrawing,
        textEditState, commitTextEdit, cancelTextEdit,
    };
};

export default useDrawingInteraction;
