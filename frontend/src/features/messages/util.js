const RELATIVE = new Intl.RelativeTimeFormat('tr-TR', { numeric: 'auto' });

export const MAX_BODY = 2000;
export const PAGE_SIZE = 20;

export function relTime(iso) {
  const ts = new Date(iso).getTime();
  const diff = Math.round((ts - Date.now()) / 1000);
  const abs = Math.abs(diff);
  if (abs < 60) return RELATIVE.format(diff, 'second');
  if (abs < 3600) return RELATIVE.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return RELATIVE.format(Math.round(diff / 3600), 'hour');
  return RELATIVE.format(Math.round(diff / 86400), 'day');
}

export function shortSub(sub) {
  if (!sub) return '—';
  return sub.length > 12 ? `${sub.slice(0, 6)}…${sub.slice(-4)}` : sub;
}
