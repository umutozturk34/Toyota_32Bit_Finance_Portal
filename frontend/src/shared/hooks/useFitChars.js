import { useRef, useState, useLayoutEffect } from 'react';

// Measures the host element's available width and reports how many average monospace glyphs fit, so a
// money formatter can pick the fullest non-overflowing string. The host MUST be width-constrained by its
// parent (e.g. a block / flex-item with `min-w-0 truncate`) so clientWidth reflects available space, not
// content — this both makes the budget meaningful and keeps the ResizeObserver from looping (changing the
// text never changes the measured width). Returns `Infinity` until the first measurement so the initial
// render shows the full value, then narrows before paint (useLayoutEffect) with no visible flicker.
export default function useFitChars() {
  const ref = useRef(null);
  const [chars, setChars] = useState(Infinity);

  useLayoutEffect(() => {
    const el = ref.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const measure = () => {
      const width = el.clientWidth;
      if (!width) return;
      const fontSize = parseFloat(getComputedStyle(el).fontSize) || 16;
      const glyph = fontSize * 0.62; // monospace digit advance ≈ 0.6em, padded so we under-fill, never overflow
      setChars(Math.max(4, Math.floor(width / glyph)));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return [ref, chars];
}
