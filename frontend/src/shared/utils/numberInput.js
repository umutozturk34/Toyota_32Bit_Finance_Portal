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
