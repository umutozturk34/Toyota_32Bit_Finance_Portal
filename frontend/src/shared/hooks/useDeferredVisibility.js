import { useEffect, useRef, useState } from 'react';

/**
 * Two-gate mount helper: returns a ref + boolean that flips true only when
 * the node has intersected the viewport AND a stagger delay has elapsed.
 * Lets heavy children (ECharts, canvas) defer their initial render so
 * mounting many siblings does not block one frame.
 *
 * @param {number} delayMs - stagger delay before considering ready
 * @param {{rootMargin?: string}} [opts]
 * @returns {[React.RefObject<HTMLElement>, boolean]}
 */
export default function useDeferredVisibility(delayMs = 0, opts = {}) {
  const ref = useRef(null);
  const [visible, setVisible] = useState(() => typeof IntersectionObserver === 'undefined');
  const [delayed, setDelayed] = useState(delayMs <= 0);

  useEffect(() => {
    if (delayed) return undefined;
    const id = window.setTimeout(() => setDelayed(true), delayMs);
    return () => window.clearTimeout(id);
  }, [delayMs, delayed]);

  useEffect(() => {
    const node = ref.current;
    if (!node || typeof IntersectionObserver === 'undefined') return undefined;
    const io = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) {
        setVisible(true);
        io.disconnect();
      }
    }, { rootMargin: opts.rootMargin ?? '120px' });
    io.observe(node);
    return () => io.disconnect();
  }, [opts.rootMargin]);

  return [ref, visible && delayed];
}
