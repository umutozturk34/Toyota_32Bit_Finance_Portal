import { useEffect } from 'react';
import { messageService } from '../services/messageService';

const HEARTBEAT_INTERVAL_MS = 30_000;

/**
 * Tells the backend "I am currently viewing conversation `key`" so that incoming
 * messages on that thread skip notifications + email and are auto-marked read.
 *
 * @param {string|null|undefined} key - conversation key, e.g. 'admin' for the user
 *   side or `user:{userSub}` for the admin side. Falsy values disable the hook.
 */
export function useActiveConversation(key) {
  useEffect(() => {
    if (!key) return undefined;
    let cancelled = false;

    const heartbeat = () => {
      messageService.registerActive(key).catch(() => {});
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
  }, [key]);
}
