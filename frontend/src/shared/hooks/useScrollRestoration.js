import { useEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';
import { TIMINGS } from '../config/uiConfig';
import useNavigationStore from '../stores/useNavigationStore';

export default function useScrollRestoration() {
  const { pathname } = useLocation();
  const navigationType = useNavigationType();
  const saveScroll = useNavigationStore((s) => s.saveScroll);
  const consumeScroll = useNavigationStore((s) => s.consumeScroll);

  useEffect(() => {
    if (navigationType !== 'POP') return undefined;
    const saved = consumeScroll(pathname);
    if (!saved) return undefined;
    const restore = () => window.scrollTo({ top: saved.y, behavior: 'instant' });
    const raf = requestAnimationFrame(restore);
    const fallback = setTimeout(restore, TIMINGS.SCROLL_RESTORE_FALLBACK_MS ?? 120);
    return () => {
      cancelAnimationFrame(raf);
      clearTimeout(fallback);
    };
  }, [pathname, navigationType, consumeScroll]);

  useEffect(() => {
    const releaseTimer = setTimeout(() => {
      try { document.body.style.minHeight = ''; } catch { /* ignore */ }
    }, TIMINGS.SCROLL_MIN_HEIGHT_RELEASE_MS);

    let timer = null;
    const flush = () => {
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
      try {
        saveScroll(pathname, window.scrollY, document.documentElement.scrollHeight);
      } catch { /* ignore */ }
    };
    const save = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(flush, TIMINGS.SCROLL_SAVE_DEBOUNCE_MS);
    };
    window.addEventListener('scroll', save, { passive: true });
    window.addEventListener('beforeunload', flush);
    return () => {
      flush();
      clearTimeout(releaseTimer);
      window.removeEventListener('scroll', save);
      window.removeEventListener('beforeunload', flush);
    };
  }, [pathname, saveScroll]);
}
