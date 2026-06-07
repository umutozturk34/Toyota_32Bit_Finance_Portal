function sanitize(code) {
  return code.toLowerCase().replace(/[^a-z0-9]/g, '_');
}

export function commodityName(t, code, fallback) {
  if (!code) return fallback ?? '';
  const key = `commodity.name.${sanitize(code)}`;
  return t(key, { defaultValue: fallback ?? code });
}

// Localized currency name for a forex code (USD -> "ABD Doları" / "US Dollar"). The backend name is the
// TCMB Turkish label, so without this an English UI showed Turkish currency names; routed through i18n
// (forex.name.*) it's locale-correct everywhere, mirroring commodityName.
export function forexName(t, code, fallback) {
  if (!code) return fallback ?? '';
  const key = `forex.name.${sanitize(code)}`;
  return t(key, { defaultValue: fallback ?? code });
}

// Generic asset-name resolver for the shared/list renderers (search, watchlist, alerts, positions,
// compare): commodities and forex get their localized name via the i18n key, every other asset type keeps
// the caller's already-resolved fallback unchanged. Accepts the market/asset-type field under either name.
export function commodityLabel(t, type, code, fallback) {
  if (type === 'COMMODITY') return commodityName(t, code, fallback);
  if (type === 'FOREX') return forexName(t, code, fallback);
  return fallback ?? '';
}
