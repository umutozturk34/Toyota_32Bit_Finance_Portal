export { relativeTime as relTime } from '../../shared/utils/dates';

export const MAX_BODY = 2000;
export const PAGE_SIZE = 20;

export function shortSub(sub) {
  if (!sub) return '—';
  return sub.length > 12 ? `${sub.slice(0, 6)}…${sub.slice(-4)}` : sub;
}
