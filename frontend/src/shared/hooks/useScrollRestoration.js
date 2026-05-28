import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { TIMINGS } from '../config/uiConfig';
import useNavigationStore from '../stores/useNavigationStore';

const RESTORE_DELAYS = [0, 50, 120, 250, 500, 900];
const SCROLL_KEYS = new Set(['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' ', 'Spacebar']);

export default function useScrollRestoration() {
  const { pathname, search } = useLocation();
  const fullKey = pathname + (search || '');
  const saveScroll = useNavigationStore((s) => s.saveScroll);
  const consumeScroll = useNavigationStore((s) => s.consumeScroll);
  const lastKeyRef = useRef(fullKey);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    if (document.body.dataset.tourActive === '1') return undefined;
    const saved = consumeScroll(fullKey);
    if (!saved || !saved.y) return undefined;

    document.body.style.minHeight = `${Math.max(saved.h || 0, saved.y + window.innerHeight)}px`;

    let cancelled = false;
    const timers = [];
    let release = 0;

    const abort = () => {
      if (cancelled) return;
      cancelled = true;
      timers.forEach(window.clearTimeout);
      window.clearTimeout(release);
      document.body.style.minHeight = '';
      window.removeEventListener('wheel', abort);
      window.removeEventListener('touchstart', abort);
      window.removeEventListener('pointerdown', abort);
      window.removeEventListener('keydown', onKeyDown);
    };
    const onKeyDown = (e) => { if (SCROLL_KEYS.has(e.key)) abort(); };

    window.addEventListener('wheel', abort, { passive: true });
    window.addEventListener('touchstart', abort, { passive: true });
    window.addEventListener('pointerdown', abort);
    window.addEventListener('keydown', onKeyDown);

    const restore = () => { if (!cancelled) window.scrollTo(0, saved.y); };
    RESTORE_DELAYS.forEach((delay) => timers.push(window.setTimeout(restore, delay)));
    release = window.setTimeout(abort, RESTORE_DELAYS[RESTORE_DELAYS.length - 1] + 200);

    return abort;
  }, [fullKey, consumeScroll]);

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    let debounceId = null;
    const flush = () => {
      if (debounceId) {
        window.clearTimeout(debounceId);
        debounceId = null;
      }
      if (document.body.dataset.tourActive === '1') return;
      try {
        saveScroll(fullKey, window.scrollY, document.documentElement.scrollHeight);
      } catch { /* swallow */ }
    };
    const queueSave = () => {
      if (document.body.dataset.tourActive === '1') return;
      if (debounceId) window.clearTimeout(debounceId);
      debounceId = window.setTimeout(flush, TIMINGS.SCROLL_SAVE_DEBOUNCE_MS ?? 200);
    };
    window.addEventListener('scroll', queueSave, { passive: true });
    window.addEventListener('beforeunload', flush);
    return () => {
      flush();
      lastKeyRef.current = fullKey;
      window.removeEventListener('scroll', queueSave);
      window.removeEventListener('beforeunload', flush);
    };
  }, [fullKey, saveScroll]);
}
