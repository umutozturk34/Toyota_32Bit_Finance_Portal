const RELATIVE_TR = new Intl.RelativeTimeFormat('tr-TR', { numeric: 'auto' });

export function relativeTime(iso) {
  if (!iso) return '';
  const ts = new Date(iso).getTime();
  const diff = Math.round((ts - Date.now()) / 1000);
  const abs = Math.abs(diff);
  if (abs < 60) return RELATIVE_TR.format(diff, 'second');
  if (abs < 3600) return RELATIVE_TR.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return RELATIVE_TR.format(Math.round(diff / 3600), 'hour');
  return RELATIVE_TR.format(Math.round(diff / 86400), 'day');
}
