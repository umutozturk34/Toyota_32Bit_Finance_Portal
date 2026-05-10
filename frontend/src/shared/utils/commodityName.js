function sanitize(code) {
  return code.toLowerCase().replace(/[^a-z0-9]/g, '_');
}

export function commodityName(t, code, fallback) {
  if (!code) return fallback ?? '';
  const key = `commodity.name.${sanitize(code)}`;
  return t(key, { defaultValue: fallback ?? code });
}
