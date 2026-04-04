import { useCallback } from 'react';
import { FIB_LEVELS, FIB_LABELS } from '../utils/fibonacci';
import { drawSnapIndicator } from '../utils/magnetManager';

const useDrawingRenderer = ({
    canvasOverlayRef,
    chartRef, candleSeriesRef,
    isDark,
    drawings, fibTools,
    isDrawing, startPoint, currentPoint,
    activeTool, activeFibTool,
    drawClicksRef, magnetManagerRef,
    pixelToChartCoords,
    chartCoordsToPixel,
}) => {

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
                    let fs = 14;
                    if (d.priceFontHeight) {
                        const pTop = chartCoordsToPixel(d.time, d.price + d.priceFontHeight);
                        if (pTop) fs = Math.abs(p.y - pTop.y);
                    } else if (d.fontSize) {
                        fs = d.fontSize;
                    }
                    fs = Math.max(6, Math.min(200, fs));
                    ctx.font = `${fs}px Inter, sans-serif`;
                    const metrics = ctx.measureText(d.content);
                    const pad = Math.max(2, fs * 0.3);
                    const boxH = fs + pad * 2;
                    ctx.fillStyle = isDark ? 'rgba(0,0,0,0.7)' : 'rgba(255,255,255,0.85)';
                    ctx.fillRect(p.x - pad, p.y - fs - pad + 2, metrics.width + pad * 2, boxH);
                    ctx.strokeStyle = isDark ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.15)';
                    ctx.lineWidth = 1;
                    ctx.strokeRect(p.x - pad, p.y - fs - pad + 2, metrics.width + pad * 2, boxH);
                    ctx.fillStyle = d.color || (isDark ? '#EDEDEF' : '#0f172a');
                    ctx.fillText(d.content, p.x, p.y);
                }
            } else if (d.type === 'ICON') {
                const p = chartCoordsToPixel(d.time, d.price);
                if (p) {
                    const emoji = d.iconId || '⭐';
                    let size = d.iconSize || 22;
                    if (d.priceFontHeight) {
                        const pTop = chartCoordsToPixel(d.time, d.price + d.priceFontHeight);
                        if (pTop) size = Math.abs(p.y - pTop.y);
                    }
                    size = Math.max(8, Math.min(300, size));
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
    }, [drawings, fibTools, isDrawing, startPoint, currentPoint, activeTool, activeFibTool, chartCoordsToPixel, pixelToChartCoords, isDark]);

    return { renderDrawings };
};

export default useDrawingRenderer;
