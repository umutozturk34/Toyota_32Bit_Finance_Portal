import { useState, useEffect } from 'react';

/**
 * Current root font-size relative to the 16px design base.
 *
 * The app's master scale lives in a fluid `html { font-size: clamp(...) }` (see index.css), so every
 * rem-based dimension already tracks the viewport. This hook exposes that same factor to the few
 * pixel-driven surfaces that CSS rem can't reach — chiefly react-grid-layout, whose row height and
 * margins are JS numbers measured in px — so they scale in lockstep instead of distorting on wide screens.
 */
const readScale = () => {
  if (typeof window === 'undefined') return 1;
  const fontSize = parseFloat(getComputedStyle(document.documentElement).fontSize);
  return fontSize > 0 ? fontSize / 16 : 1;
};

export default function useRootFontScale() {
  const [scale, setScale] = useState(readScale);

  useEffect(() => {
    const update = () => setScale(readScale());
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);

  return scale;
}
