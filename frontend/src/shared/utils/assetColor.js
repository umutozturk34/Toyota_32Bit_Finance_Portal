// Deterministic per-asset badge colour — a keyless logo substitute so each code gets its own consistent hue
// instead of one flat per-type colour. The palette is harmonised with the app theme (accent indigo/violet
// family, all at a consistent 400-level tone) and deliberately EXCLUDES pure green/red so an asset's badge is
// never confused with the gain/loss (success/danger) signalling used elsewhere.
const ASSET_PALETTE = [
  '#818cf8', // indigo (accent-bright)
  '#a78bfa', // violet (accent-secondary)
  '#c084fc', // purple
  '#e879f9', // fuchsia
  '#f472b6', // pink
  '#fb923c', // orange
  '#fbbf24', // amber
  '#a3e635', // lime
  '#2dd4bf', // teal
  '#22d3ee', // cyan
  '#38bdf8', // sky
  '#60a5fa', // blue
];

/** Stable colour for an asset, derived from its code so the same asset always renders the same hue. */
export function assetColor(seed = '') {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  }
  return ASSET_PALETTE[hash % ASSET_PALETTE.length];
}

/** Tinted background + solid foreground for a badge, matching the app's soft tinted-chip aesthetic. */
export function assetColorStyle(seed = '') {
  const color = assetColor(seed);
  return { backgroundColor: `${color}22`, color };
}
