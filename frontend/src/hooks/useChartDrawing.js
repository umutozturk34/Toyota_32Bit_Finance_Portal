import { useEffect, useRef, useState, useCallback } from 'react';
import { calculateFibRetracement, calculateFibExtension, FIB_LEVELS, FIB_LABELS } from '../utils/fibonacci';
import { createMagnetManager, drawSnapIndicator } from '../utils/magnetManager';

const useChartDrawing = ({
    chartRef, candleSeriesRef, candleDataRef,
    isDark,
    drawings, addDrawing, cancelTool,
    fibTools, addFibTool, cancelFibTool,
    activeTool, activeFibTool,
    magnetMode,
    textInput, textSize, selectedIcon,
    data, symbol,
    renderDrawingsRef,
    selectTool, selectFibTool,
}) => {
    const canvasOverlayRef = useRef(null);
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

    const isAnyToolActive = activeTool || activeFibTool;

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

    const rawPixelToChart = useCallback((x, y) => {
        const chart = chartRef.current;
        if (!chart) return null;
        try {
            const time = chart.timeScale().coordinateToTime(x);
            const price = candleSeriesRef.current?.coordinateToPrice(y);
            if (time && price !== null) return { time, price };
        } catch { }
        return null;
    }, []);

    const pixelToChartCoords = useCallback((x, y) => {
        const mgr = magnetManagerRef.current;
        if (mgr) return mgr.snapImmediate(x, y);
        return rawPixelToChart(x, y);
    }, [rawPixelToChart]);

    const chartCoordsToPixel = useCallback((time, price) => {
        const chart = chartRef.current;
        if (!chart || !candleSeriesRef.current) return null;
        try {
            const x = chart.timeScale().timeToCoordinate(time);
            const y = candleSeriesRef.current.priceToCoordinate(price);
            if (x !== null && y !== null) return { x, y };
        } catch { }
        return null;
    }, []);

    const renderDrawings = useCallback(() => {
        const canvas = canvasOverlayRef.current;
        const chart = chartRef.current;
        if (!canvas || !chart) return;
        const ctx = canvas.getContext('2d');
        const rect = canvas.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;
        canvas.width = rect.width * dpr;
        canvas.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        ctx.clearRect(0, 0, rect.width, rect.height);
        drawings.forEach(d => {
            ctx.lineWidth = 2;
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            ctx.strokeStyle = d.color || '#5E6AD2';
            ctx.setLineDash([]);
            if (d.type === 'TREND_LINE') {
                const s = chartCoordsToPixel(d.startTime, d.startPrice);
                const e = chartCoordsToPixel(d.endTime, d.endPrice);
                if (s && e) { ctx.beginPath(); ctx.moveTo(s.x, s.y); ctx.lineTo(e.x, e.y); ctx.stroke(); }
            } else if (d.type === 'HORIZONTAL_LINE') {
                const p = chartCoordsToPixel(d.time, d.price);
                if (p) {
                    ctx.setLineDash([5, 5]);
                    ctx.beginPath(); ctx.moveTo(0, p.y); ctx.lineTo(rect.width, p.y); ctx.stroke();
                    ctx.setLineDash([]);
                    ctx.fillStyle = d.color || '#f59e0b';
                    ctx.fillRect(rect.width - 80, p.y - 10, 75, 20);
                    ctx.fillStyle = '#fff'; ctx.font = '11px Inter, sans-serif';
                    ctx.fillText(`$${d.price.toFixed(2)}`, rect.width - 75, p.y + 4);
                }
            } else if (d.type === 'VERTICAL_LINE') {
                const p = chartCoordsToPixel(d.time, d.price);
                if (p) {
                    ctx.setLineDash([5, 5]);
                    ctx.beginPath(); ctx.moveTo(p.x, 0); ctx.lineTo(p.x, rect.height); ctx.stroke();
                    ctx.setLineDash([]);
                }
            } else if (d.type === 'FREEHAND' && d.points) {
                const pts = d.points.map(pt => chartCoordsToPixel(pt.time, pt.price)).filter(Boolean);
                if (pts.length > 1) {
                    ctx.beginPath(); ctx.moveTo(pts[0].x, pts[0].y);
                    for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i].x, pts[i].y);
                    ctx.stroke();
                }
            } else if (d.type === 'TEXT') {
                const p = chartCoordsToPixel(d.time, d.price);
                if (p) {
                    const fs = d.fontSize || 13;
                    ctx.font = `${fs}px Inter, sans-serif`;
                    const metrics = ctx.measureText(d.content);
                    const pad = 4;
                    const boxH = fs + pad * 2;
                    ctx.fillStyle = isDark ? 'rgba(0,0,0,0.7)' : 'rgba(255,255,255,0.85)';
                    ctx.fillRect(p.x - pad, p.y - fs - pad + 2, metrics.width + pad * 2, boxH);
                    ctx.strokeStyle = isDark ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.15)';
                    ctx.lineWidth = 1;
                    ctx.strokeRect(p.x - pad, p.y - fs - pad + 2, metrics.width + pad * 2, boxH);
                    ctx.fillStyle = d.color || (isDark ? '#EDEDEF' : '#1f2937');
                    ctx.fillText(d.content, p.x, p.y);
                }
            } else if (d.type === 'ICON') {
                const p = chartCoordsToPixel(d.time, d.price);
                if (p) {
                    const emoji = d.iconId || '⭐';
                    const size = 22;
                    ctx.font = `${size}px serif`;
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillText(emoji, p.x, p.y);
                    ctx.textAlign = 'start';
                    ctx.textBaseline = 'alphabetic';
                }
            }
        });
        fibTools.forEach(f => {
            const levels = f.levels || [];
            const s = chartCoordsToPixel(f.startTime, f.startPrice);
            const e = chartCoordsToPixel(f.endTime, f.endPrice);
            if (!s || !e) return;
            levels.forEach(lev => {
                const fp = chartCoordsToPixel(f.startTime, lev.price);
                if (!fp) return;
                const isKey = lev.level === 0.5 || lev.level === 0.618;
                ctx.strokeStyle = f.type === 'EXTENSION'
                    ? `rgba(249,115,22,${isKey ? 1 : 0.6})`
                    : `rgba(236,72,153,${isKey ? 1 : 0.6})`;
                ctx.lineWidth = isKey ? 2 : 1;
                ctx.setLineDash(lev.level === 0 || lev.level === 1 ? [] : [5, 5]);
                ctx.beginPath();
                ctx.moveTo(Math.min(s.x, e.x), fp.y);
                ctx.lineTo(Math.max(s.x, e.x), fp.y);
                ctx.stroke();
                ctx.fillStyle = f.type === 'EXTENSION' ? 'rgba(249,115,22,0.9)' : 'rgba(236,72,153,0.9)';
                ctx.font = '10px Inter, sans-serif';
                ctx.fillText(`${lev.label} (${lev.price.toFixed(2)})`, Math.max(s.x, e.x) + 5, fp.y + 4);
            });
            ctx.setLineDash([]);
        });
        if (isDrawing && startPoint && currentPoint && activeTool && activeTool !== 'FREEHAND') {
            ctx.strokeStyle = '#5E6AD2';
            ctx.lineWidth = 2;
            ctx.setLineDash([3, 3]);
            if (activeTool === 'TREND_LINE') {
                ctx.beginPath(); ctx.moveTo(startPoint.x, startPoint.y);
                ctx.lineTo(currentPoint.x, currentPoint.y); ctx.stroke();
            } else if (activeTool === 'HORIZONTAL_LINE') {
                ctx.beginPath(); ctx.moveTo(0, startPoint.y);
                ctx.lineTo(rect.width, startPoint.y); ctx.stroke();
            } else if (activeTool === 'VERTICAL_LINE') {
                ctx.beginPath(); ctx.moveTo(startPoint.x, 0);
                ctx.lineTo(startPoint.x, rect.height); ctx.stroke();
            }
            ctx.setLineDash([]);
        }
        if (activeFibTool && drawClicksRef.current.length >= 1 && currentPoint) {
            const firstClick = drawClicksRef.current[0];
            const sc = pixelToChartCoords(firstClick.x, firstClick.y);
            const ec = pixelToChartCoords(currentPoint.x, currentPoint.y);
            if (sc && ec) {
                const range = ec.price - sc.price;
                FIB_LEVELS.forEach(level => {
                    const fibPrice = sc.price + range * level;
                    const fp = chartCoordsToPixel(sc.time, fibPrice);
                    if (fp) {
                        const isKey = level === 0.5 || level === 0.618;
                        ctx.strokeStyle = `rgba(236,72,153,${isKey ? 0.7 : 0.35})`;
                        ctx.lineWidth = 1;
                        ctx.setLineDash([3, 3]);
                        ctx.beginPath();
                        ctx.moveTo(Math.min(firstClick.x, currentPoint.x), fp.y);
                        ctx.lineTo(Math.max(firstClick.x, currentPoint.x), fp.y);
                        ctx.stroke();
                        ctx.fillStyle = 'rgba(236,72,153,0.5)';
                        ctx.font = '9px Inter, sans-serif';
                        ctx.fillText(FIB_LABELS[level], Math.max(firstClick.x, currentPoint.x) + 3, fp.y + 3);
                    }
                });
                ctx.setLineDash([]);
            }
        }
        const mgr = magnetManagerRef.current;
        if (mgr) {
            drawSnapIndicator(ctx, mgr.getSnap(), { width: rect.width, height: rect.height });
        }
    }, [drawings, fibTools, isDrawing, startPoint, currentPoint, activeTool, activeFibTool, chartCoordsToPixel, pixelToChartCoords, magnetMode, isAnyToolActive, isDark]);

    useEffect(() => {
        renderDrawingsRef.current = renderDrawings;
    });

    useEffect(() => { renderDrawings(); }, [renderDrawings]);

    useEffect(() => {
        if (drawings.length > 0 || fibTools.length > 0) {
            let id;
            const animate = () => { renderDrawings(); id = requestAnimationFrame(animate); };
            id = requestAnimationFrame(animate);
            return () => cancelAnimationFrame(id);
        }
    }, [drawings, fibTools, renderDrawings]);

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
        return { x: e.clientX - rect.left, y: e.clientY - rect.top };
    };

    const handleMouseDown = (e) => {
        if (!isAnyToolActive) return;
        e.preventDefault();
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
                    color: activeTool === 'HORIZONTAL_LINE' ? '#f59e0b' : '#06b6d4',
                });
            }
            cancelTool();
            return;
        }
        if (activeTool === 'TEXT') {
            const coords = pixelToChartCoords(pos.x, pos.y);
            if (coords) {
                addDrawing({
                    type: 'TEXT',
                    time: coords.time,
                    price: coords.price,
                    content: textInput || 'Text',
                    fontSize: textSize,
                    color: isDark ? '#EDEDEF' : '#1f2937',
                });
            }
            cancelTool();
            return;
        }
        if (activeTool === 'ICON') {
            const coords = pixelToChartCoords(pos.x, pos.y);
            if (coords) {
                addDrawing({
                    type: 'ICON',
                    time: coords.time,
                    price: coords.price,
                    iconId: selectedIcon,
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
                fctx.strokeStyle = '#10b981';
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
            if (chartPoints.length > 1) addDrawing({ type: 'FREEHAND', points: chartPoints, color: '#10b981' });
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
                    color: '#5E6AD2',
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
        cancelTool();
    };

    const handleMouseLeave = () => {
        if (magnetManagerRef.current) magnetManagerRef.current.clear();
        if (isDrawingRef.current && activeTool === 'FREEHAND' && freehandPointsRef.current.length > 1) {
            const chartPoints = freehandPointsRef.current
                .map(p => { const c = pixelToChartCoords(p.x, p.y); return c ? { time: c.time, price: c.price } : null; })
                .filter(Boolean);
            if (chartPoints.length > 1) addDrawing({ type: 'FREEHAND', points: chartPoints, color: '#10b981' });
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
        canvasOverlayRef, freehandCanvasRef,
        handleMouseDown, handleMouseMove, handleMouseUp, handleMouseLeave,
        isAnyToolActive, handleSelectTool, handleSelectFibTool, cancelAllDrawing,
    };
};

export default useChartDrawing;
