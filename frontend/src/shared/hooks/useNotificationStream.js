import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { EventSourcePolyfill } from 'event-source-polyfill';
import { useAuth } from '../../features/auth/AuthContext';
import { getToken } from '../../features/auth/keycloak';
import { toast } from '../components/Toast';

const STREAM_URL = '/api/v1/notifications/stream';
const RECONNECT_DELAY_MS = 4_000;

function playChime() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
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
    osc.onended = () => ctx.close();
  } catch {
    /* AudioContext unavailable — silent */
  }
}

export default function useNotificationStream() {
  const { isAuthenticated, loading } = useAuth();
  const queryClient = useQueryClient();
  const sourceRef = useRef(null);
  const reconnectTimerRef = useRef(null);

  useEffect(() => {
    if (!isAuthenticated || loading) return undefined;
    let cancelled = false;

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

        source.addEventListener('notification', (event) => {
          let payload = null;
          try { payload = JSON.parse(event.data); } catch { /* malformed event */ }
          queryClient.setQueryData(['notifications', 'unread-count'], (old) => (Number(old) || 0) + 1);
          queryClient.invalidateQueries({ queryKey: ['notifications'] });
          playChime();
          if (payload?.title) {
            toast.info(payload.title, payload.body ?? undefined);
          }
        });

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
