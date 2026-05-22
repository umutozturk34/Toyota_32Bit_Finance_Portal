const AUTO_DEFAULT_NAMES = new Set(['Favoriler', 'Favorites']);

export function watchlistName(t, list) {
  const raw = list?.name ?? '';
  if (list?.isDefault || AUTO_DEFAULT_NAMES.has(raw)) {
    return t('watch.defaultListName', { defaultValue: raw });
  }
  return raw;
}

export function localizeWatchlistName(t, name) {
  if (!name) return '';
  if (AUTO_DEFAULT_NAMES.has(name)) {
    return t('watch.defaultListName', { defaultValue: name });
  }
  return name;
}
