import { useState, useCallback, useEffect } from 'react';

/**
 * Transient highlight pulse for a drawing/fibonacci when the user clicks it in the sidebar list: a 2.2s
 * fade-out driven by requestAnimationFrame, repainting via the shared renderDrawingsRef each frame. Returns the
 * live highlight state plus per-kind triggers the sidebar wires to its list rows.
 */
export default function useHighlightAnimation(renderDrawingsRef) {
    const [highlight, setHighlight] = useState(null);

    const triggerHighlight = useCallback((kind, id) => {
        if (!id) return;
        setHighlight({ kind, id, startMs: Date.now() });
    }, []);

    const highlightDrawing = useCallback((id) => triggerHighlight('drawing', id), [triggerHighlight]);
    const highlightFib = useCallback((id) => triggerHighlight('fib', id), [triggerHighlight]);

    useEffect(() => {
        if (!highlight) return undefined;
        const DURATION = 2200;
        let raf;
        const tick = () => {
            const elapsed = Date.now() - highlight.startMs;
            renderDrawingsRef.current?.();
            if (elapsed > DURATION) {
                setHighlight(null);
                return;
            }
            raf = requestAnimationFrame(tick);
        };
        raf = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(raf);
    }, [highlight, renderDrawingsRef]);

    return { highlight, highlightDrawing, highlightFib };
}
