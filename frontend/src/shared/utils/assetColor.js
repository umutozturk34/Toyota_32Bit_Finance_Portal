// Deterministic per-asset badge colour — a keyless logo substitute so each code gets its own consistent hue
// instead of one flat per-type colour. The palette is intentionally RESTRAINED to a cohesive cool family that
// flows from the brand indigo through blue to teal (all 400-level), so a wall of badges reads as one calm,
// on-theme set rather than a rainbow. It deliberately EXCLUDES pure green/red so a badge is never confused with
// the gain/loss (success/danger) signalling, and drops the loud warm/magenta hues that made the grid noisy.
const ASSET_PALETTE = [
  '#818cf8', // indigo (accent-bright)
  '#a78bfa', // violet (accent-secondary)
  '#60a5fa', // blue
  '#38bdf8', // sky
  '#22d3ee', // cyan
  '#2dd4bf', // teal
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
