import i18n from '../i18n/config';

function relativeFormatter() {
  const lang = i18n.language || i18n.options.fallbackLng || 'en';
  const tag = lang === 'tr' ? 'tr-TR' : 'en-US';
  return new Intl.RelativeTimeFormat(tag, { numeric: 'auto' });
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
