// Upper-bound limits for numeric <input>s, mirroring the backend Bean Validation @DecimalMax bounds so
// a value the server would reject can't even be typed past the cap. Money/price ≤ 1e12, quantity/lot ≤ 1e9,
// percent threshold ≤ 999.9999.
export const MAX_MONEY = 1_000_000_000_000;
export const MAX_QUANTITY = 1_000_000_000;
export const MAX_PERCENT = 999.9999;

// Silent clamp for controlled numeric inputs: returns the raw keystroke unchanged while it's empty or a
// partial number (e.g. "", "-", "1."), so typing stays fluid, and only rewrites it to String(max) once the
// parsed value exceeds max. Keeps state as a string (every caller stores the input value verbatim) and never
// fabricates an i18n message — the cap is enforced by quietly capping the value.
export function clampNumberInput(raw, max) {
  if (raw === '' || raw == null) return raw;
  const n = Number(raw);
  if (!Number.isFinite(n)) return raw;
  return n > max ? String(max) : raw;
}

// Decimal-place bounds mirroring the backend @Digits and the displayed precision so a value can't be
// typed finer than it would ever be shown or stored: prices keep 8 fraction digits; quantities keep 6
// (matching the 6-dp quantity display and the 0.000001 min-quantity floor — finer input would silently
// round to 0). Share-based assets are further constrained to whole units by the caller.
export const PRICE_DECIMALS = 8;
export const QUANTITY_DECIMALS = 6;
// VIOP lots are whole-ish: the backend caps closeQuantityLot/quantityLot at @Digits fraction=4.
export const LOT_DECIMALS = 4;

// Trim a controlled numeric input's fractional tail to maxFraction digits (mirrors @Digits). Leaves an
// in-progress "1." or a plain integer untouched so typing stays fluid; only cuts once the tail is too long.
export function clampDecimals(raw, maxFraction) {
  if (raw === '' || raw == null) return raw;
  const s = String(raw);
  const dot = s.indexOf('.');
  if (dot < 0) return s;
  if (s.length - dot - 1 <= maxFraction) return s;
  return s.slice(0, dot + 1 + maxFraction);
}

// One-shot sanitizer for money/quantity inputs: cap the magnitude AND the decimal places at once.
export function sanitizeNumberInput(raw, max, maxFraction) {
  return clampDecimals(clampNumberInput(raw, max), maxFraction);
}
