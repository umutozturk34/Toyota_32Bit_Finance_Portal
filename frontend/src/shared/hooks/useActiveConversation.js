import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { messageService } from '../services/messageService';

const HEARTBEAT_INTERVAL_MS = 30_000;

/**
 * Tells the backend "I am currently viewing conversation `key`" so that incoming
 * messages on that thread skip notifications + email and are auto-marked read.
 *
 * Each successful register also invalidates the messages cache so the bulk
 * mark-read performed by `MessagePresenceService.register` reflects in the UI
 * without a full page refresh.
 *
 * @param {string|null|undefined} key - conversation key, e.g. 'admin' for the user
 *   side or `user:{userSub}` for the admin side. Falsy values disable the hook.
 */
export function useActiveConversation(key) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!key) return undefined;
    let cancelled = false;

    const heartbeat = () => {
      messageService.registerActive(key)
        .then(() => {
          if (!cancelled) queryClient.invalidateQueries({ queryKey: ['messages'] });
        })
        .catch(() => {});
    };

    heartbeat();
    const intervalId = setInterval(() => {
      if (!cancelled) heartbeat();
    }, HEARTBEAT_INTERVAL_MS);

    return () => {
      cancelled = true;
      clearInterval(intervalId);
      messageService.clearActive().catch(() => {});
    };
  }, [key, queryClient]);
}
