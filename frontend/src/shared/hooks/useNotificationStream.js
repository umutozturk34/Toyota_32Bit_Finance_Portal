import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { useAuth } from '../../features/auth/useAuth';
import { getToken } from '../../features/auth/lib/keycloak';
import { toast } from '../components/feedback/toastBus';
import i18n from '../i18n/config';

const STREAM_URL = '/api/v1/notifications/stream';
const RECONNECT_DELAY_MS = 4_000;

let sharedCtx = null;
let listenersAttached = false;

function audioCtx() {
  if (!sharedCtx) {
    const Ctor = window.AudioContext || window.webkitAudioContext;
    if (Ctor) sharedCtx = new Ctor();
  }
  return sharedCtx;
}

function attachUnlockListeners() {
  if (listenersAttached) return;
  listenersAttached = true;
  const resume = () => audioCtx()?.resume?.();
  window.addEventListener('pointerdown', resume);
  window.addEventListener('keydown', resume);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) resume();
  });
}

function playChime() {
  const ctx = audioCtx();
  if (!ctx) return;
  if (ctx.state === 'suspended') ctx.resume();
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.connect(gain);
  gain.connect(ctx.destination);
  osc.type = 'sine';
  osc.frequency.value = 880;
  const t = ctx.currentTime;
  gain.gain.setValueAtTime(0.0001, t);
  gain.gain.exponentialRampToValueAtTime(0.22, t + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.45);
  osc.start(t);
  osc.stop(t + 0.5);
}

export default function useNotificationStream() {
  const { isAuthenticated, loading } = useAuth();
  const queryClient = useQueryClient();
  const sourceRef = useRef(null);
  const reconnectTimerRef = useRef(null);

  useEffect(() => {
    if (!isAuthenticated || loading) return undefined;
    attachUnlockListeners();
    let cancelled = false;

    const handleNotification = (event) => {
      let payload = null;
      try { payload = JSON.parse(event.data); } catch { /* malformed */ }
      queryClient.setQueriesData(
        { predicate: (q) => q.queryKey[0] === 'notifications' && q.queryKey[q.queryKey.length - 1] === 'unread-count' },
        (old) => {
          const n = Number(old);
          return (Number.isFinite(n) ? n : 0) + 1;
        }
      );
      if (payload && payload.id != null) {
        queryClient.setQueriesData(
          {
            predicate: (q) => {
              if (q.queryKey[0] !== 'notifications') return false;
              const last = q.queryKey[q.queryKey.length - 1];
              return typeof last === 'object' && last !== null;
            },
          },
          (data) => {
            if (!data) return data;
            if (data?.pages && Array.isArray(data.pages)) {
              return {
                ...data,
                pages: data.pages.map((page, idx) => {
                  if (idx !== 0 || !page) return page;
                  if (Array.isArray(page.content) && !page.content.some((n) => n.id === payload.id)) {
                    return {
                      ...page,
                      content: [payload, ...page.content],
                      totalElements: (page.totalElements ?? 0) + 1,
                    };
                  }
                  if (Array.isArray(page.items) && !page.items.some((n) => n.id === payload.id)) {
                    return { ...page, items: [payload, ...page.items] };
                  }
                  return page;
                }),
              };
            }
            if (Array.isArray(data.content)) {
              if (data.content.some((n) => n.id === payload.id)) return data;
              return {
                ...data,
                content: [payload, ...data.content],
                totalElements: (data.totalElements ?? 0) + 1,
              };
            }
            if (Array.isArray(data.items)) {
              if (data.items.some((n) => n.id === payload.id)) return data;
              return { ...data, items: [payload, ...data.items] };
            }
            return data;
          }
        );
      }
      queryClient.invalidateQueries({
        predicate: (q) => {
          if (q.queryKey[0] !== 'notifications') return false;
          const last = q.queryKey[q.queryKey.length - 1];
          return last !== 'unread-count';
        },
      });
      playChime();
      const title = payload?.title || i18n.t('notifStream.newNotification');
      toast.info(title, payload?.body ?? undefined);
    };

    const connect = async () => {
      if (cancelled) return;
      try {
        const token = await getToken();
        if (cancelled || !token) return;
        const source = new EventSourcePolyfill(STREAM_URL, {
          headers: { Authorization: `Bearer ${token}` },
          heartbeatTimeout: 600_000,
        });
        sourceRef.current = source;

        source.addEventListener('notification', handleNotification);

        source.onerror = () => {
          source.close();
          if (!cancelled) {
            reconnectTimerRef.current = setTimeout(connect, RECONNECT_DELAY_MS);
          }
        };
      } catch {
        if (!cancelled) {
          reconnectTimerRef.current = setTimeout(connect, RECONNECT_DELAY_MS);
        }
      }
    };

    connect();
    return () => {
      cancelled = true;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      if (sourceRef.current) sourceRef.current.close();
      sourceRef.current = null;
    };
  }, [isAuthenticated, loading, queryClient]);
}
