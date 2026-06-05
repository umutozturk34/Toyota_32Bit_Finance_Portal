function sanitize(code) {
  return code.toLowerCase().replace(/[^a-z0-9]/g, '_');
}

export function commodityName(t, code, fallback) {
  if (!code) return fallback ?? '';
  const key = `commodity.name.${sanitize(code)}`;
  return t(key, { defaultValue: fallback ?? code });
}

// Generic asset-name resolver for the shared/list renderers (search, watchlist, alerts, positions,
// compare): commodities get their localized name via the i18n key, every other asset type keeps the
// caller's already-resolved fallback unchanged. Accepts the market/asset-type field under either name.
export function commodityLabel(t, type, code, fallback) {
  return type === 'COMMODITY' ? commodityName(t, code, fallback) : (fallback ?? '');
}
