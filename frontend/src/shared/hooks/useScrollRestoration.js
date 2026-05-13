import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { TIMINGS } from '../config/uiConfig';
import useNavigationStore from '../stores/useNavigationStore';

const RESTORE_DELAYS = [0, 50, 120, 250, 500, 900];

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
    const saved = consumeScroll(fullKey);
    if (!saved || !saved.y) return undefined;

    document.body.style.minHeight = `${Math.max(saved.h || 0, saved.y + window.innerHeight)}px`;
    let cancelled = false;
    const restore = () => { if (!cancelled) window.scrollTo(0, saved.y); };
    const timers = RESTORE_DELAYS.map((delay) => window.setTimeout(restore, delay));
    const release = window.setTimeout(() => {
      document.body.style.minHeight = '';
    }, RESTORE_DELAYS[RESTORE_DELAYS.length - 1] + 200);

    return () => {
      cancelled = true;
      timers.forEach(window.clearTimeout);
      window.clearTimeout(release);
    };
  }, [fullKey, consumeScroll]);

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    let debounceId = null;
    const flush = () => {
      if (debounceId) {
        window.clearTimeout(debounceId);
        debounceId = null;
      }
      try {
        saveScroll(fullKey, window.scrollY, document.documentElement.scrollHeight);
      } catch { /* swallow */ }
    };
    const queueSave = () => {
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
