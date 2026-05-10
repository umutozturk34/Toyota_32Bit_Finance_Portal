import { currentLocaleTag } from './formatters';

function relativeFormatter() {
  return new Intl.RelativeTimeFormat(currentLocaleTag(), { numeric: 'auto' });
}

export function relativeTime(iso) {
  if (!iso) return '';
  const ts = new Date(iso).getTime();
  const diff = Math.round((ts - Date.now()) / 1000);
  const abs = Math.abs(diff);
  const fmt = relativeFormatter();
  if (abs < 60) return fmt.format(diff, 'second');
  if (abs < 3600) return fmt.format(Math.round(diff / 60), 'minute');
  if (abs < 86400) return fmt.format(Math.round(diff / 3600), 'hour');
  return fmt.format(Math.round(diff / 86400), 'day');
}
