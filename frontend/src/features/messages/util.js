export { relativeTime as relTime } from '../../shared/utils/dates';

export const MAX_BODY = 2000;
export const PAGE_SIZE = 20;
export const CONVERSATION_STATUS_STALE_MS = 15_000;
export const CONVERSATION_CLOSED_BANNER = 'Yönetim sohbetinizi kapattı. Yeniden açılana kadar mesaj gönderemezsiniz.';
export const CONVERSATION_CLOSED_HINT = 'Sohbet kapatıldı. Yeniden açılana kadar mesaj gönderemezsiniz.';

export function shortSub(sub) {
  if (!sub) return '—';
  return sub.length > 12 ? `${sub.slice(0, 6)}…${sub.slice(-4)}` : sub;
}
