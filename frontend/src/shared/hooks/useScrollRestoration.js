import { useEffect } from 'react';
import { TIMINGS } from '../config/uiConfig';

const KEY_PREFIX = 'scroll-pos:';

export default function useScrollRestoration() {
  useEffect(() => {
    const releaseTimer = setTimeout(() => {
      try { document.body.style.minHeight = ''; } catch { /* ignore */ }
    }, TIMINGS.SCROLL_MIN_HEIGHT_RELEASE_MS);

    let timer = null;
    const save = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        try {
          const key = KEY_PREFIX + window.location.pathname;
          const payload = {
            y: window.scrollY,
            h: document.documentElement.scrollHeight,
          };
          sessionStorage.setItem(key, JSON.stringify(payload));
        } catch { /* ignore */ }
      }, TIMINGS.SCROLL_SAVE_DEBOUNCE_MS);
    };
    window.addEventListener('scroll', save, { passive: true });
    return () => {
      if (timer) clearTimeout(timer);
      clearTimeout(releaseTimer);
      window.removeEventListener('scroll', save);
    };
  }, []);
}
