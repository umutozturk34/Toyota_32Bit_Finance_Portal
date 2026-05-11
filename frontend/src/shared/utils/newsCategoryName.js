export function newsCategoryName(t, category) {
  if (!category) return '';
  return t(`news.categories.${category}`, { defaultValue: category });
}
