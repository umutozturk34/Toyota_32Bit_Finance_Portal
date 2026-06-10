import { useRef, useState, useCallback, useEffect } from 'react';

/**
 * Fullscreen behavior for the chart card: native Fullscreen API with a CSS-class fallback when the browser
 * rejects the request. Returns the wrapper ref to attach, the current state, and a toggle. Self-contained so the
 * chart component doesn't carry the fullscreen lifecycle.
 */
export default function useFullscreenMode() {
    const wrapperRef = useRef(null);
    const [isFullscreen, setIsFullscreen] = useState(false);

    useEffect(() => {
        const onChange = () => setIsFullscreen(document.fullscreenElement === wrapperRef.current);
        document.addEventListener('fullscreenchange', onChange);
        return () => document.removeEventListener('fullscreenchange', onChange);
    }, []);

    // Entering/exiting fullscreen swaps the container's height class, but the transition settles asynchronously
    // (native fullscreen even has a browser animation), so a single early nudge fired before the layout settled
    // and the chart kept its fullscreen height after exit ("büyüttükten sonra büyük kalıyor"). Re-dispatch a
    // resize across the transition window so the LAST one reads the settled container size; the chart area's
    // overflow-hidden keeps the canvas from inflating the box in the meantime.
    useEffect(() => {
        const fire = () => window.dispatchEvent(new Event('resize'));
        const raf = requestAnimationFrame(() => requestAnimationFrame(fire));
        const t1 = setTimeout(fire, 130);
        const t2 = setTimeout(fire, 380);
        return () => { cancelAnimationFrame(raf); clearTimeout(t1); clearTimeout(t2); };
    }, [isFullscreen]);

    const toggleFullscreen = useCallback(() => {
        const wrapper = wrapperRef.current;
        if (!wrapper) return;
        // Already in the CSS fallback (a prior native request rejected) → just drop it; never re-attempt
        // native, which would otherwise make the exit click try to ENTER fullscreen again (stuck enlarged).
        if (wrapper.classList.contains('chart-pseudo-fullscreen')) {
            wrapper.classList.remove('chart-pseudo-fullscreen');
            setIsFullscreen(false);
            return;
        }
        const nativeSupported = typeof wrapper.requestFullscreen === 'function'
            && typeof document.exitFullscreen === 'function';
        if (nativeSupported) {
            if (document.fullscreenElement === wrapper) {
                document.exitFullscreen?.();
            } else {
                wrapper.requestFullscreen?.().catch(() => {
                    wrapper.classList.add('chart-pseudo-fullscreen');
                    setIsFullscreen(true);
                });
            }
            return;
        }
        wrapper.classList.add('chart-pseudo-fullscreen');
        setIsFullscreen(true);
    }, []);

    return { isFullscreen, toggleFullscreen, wrapperRef };
}
