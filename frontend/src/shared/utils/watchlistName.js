const AUTO_DEFAULT_NAMES = new Set(['Favoriler', 'Favorites']);

export function watchlistName(t, list) {
  if (list?.isDefault) {
    return t('watch.defaultListName', { defaultValue: list?.name ?? '' });
  }
  return list?.name ?? '';
}

export function localizeWatchlistName(t, name) {
  if (!name) return '';
  if (AUTO_DEFAULT_NAMES.has(name)) {
    return t('watch.defaultListName', { defaultValue: name });
  }
  return name;
}
