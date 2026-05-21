export const STALE = Object.freeze({
  SHORT: 30_000,
  MEDIUM: 60_000,
  LONG: 1000 * 60 * 60,
  NEVER: Infinity,
});

export const GC = Object.freeze({
  DEFAULT: 1000 * 60 * 5,
  LONG: 1000 * 60 * 30,
  NEVER: Infinity,
});
