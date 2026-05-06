import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { useAuth } from '../../features/auth/AuthContext';
import { getToken } from '../../features/auth/keycloak';
import { toast } from '../components/Toast';

const STREAM_URL = '/api/v1/notifications/stream';
const RECONNECT_DELAY_MS = 4_000;

let sharedAudioContext = null;
let unlocked = false;

function getAudioContext() {
  if (!sharedAudioContext) {
    try {
      const Ctor = window.AudioContext || window.webkitAudioContext;
      if (Ctor) sharedAudioContext = new Ctor();
    } catch {
      return null;
    }
  }
  return sharedAudioContext;
}

function unlockAudioOnce() {
  if (unlocked) return;
  const handler = () => {
    const ctx = getAudioContext();
    if (ctx && ctx.state === 'suspended') ctx.resume().catch(() => {});
    unlocked = true;
    window.removeEventListener('pointerdown', handler);
    window.removeEventListener('keydown', handler);
  };
  window.addEventListener('pointerdown', handler, { once: true });
  window.addEventListener('keydown', handler, { once: true });
}

function playChime() {
  const ctx = getAudioContext();
  if (!ctx) return;
  const fire = () => {
    try {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.type = 'sine';
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.0001, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.18, ctx.currentTime + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.45);
      osc.start();
      osc.stop(ctx.currentTime + 0.5);
    } catch {
      /* play failed */
    }
  };
  if (ctx.state === 'suspended') {
    ctx.resume().then(fire).catch(() => {});
  } else {
    fire();
  }
}

export default function useNotificationStream() {
  const { isAuthenticated, loading } = useAuth();
  const queryClient = useQueryClient();
  const sourceRef = useRef(null);
  const reconnectTimerRef = useRef(null);

  useEffect(() => {
    if (!isAuthenticated || loading) return undefined;
    unlockAudioOnce();
    let cancelled = false;

    const handleNotification = (event) => {
      let payload = null;
      try { payload = JSON.parse(event.data); } catch { /* malformed */ }
      queryClient.setQueryData(['notifications', 'unread-count'], (old) => {
        const n = Number(old);
        return (Number.isFinite(n) ? n : 0) + 1;
      });
      if (payload && payload.id != null) {
        queryClient.setQueriesData(
          { predicate: (q) => q.queryKey[0] === 'notifications' && typeof q.queryKey[1] === 'object' && q.queryKey[1] !== null },
          (data) => {
            if (!data) return data;
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
        predicate: (q) => q.queryKey[0] === 'notifications' && q.queryKey[1] !== 'unread-count',
      });
      playChime();
      const title = payload?.title || 'Yeni bildirim';
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
