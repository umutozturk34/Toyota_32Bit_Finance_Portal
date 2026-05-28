import {
  ARROW_HALO_DARK,
  ARROW_HALO_LIGHT,
  ARROW_STROKE_DARK,
  ARROW_STROKE_LIGHT,
  MASK_FILL_DARK,
  MASK_FILL_LIGHT,
  SUMMARY_BACKDROP_DARK,
  SUMMARY_BACKDROP_LIGHT,
} from './constants';

const TOOLTIP_THEME_DARK = Object.freeze({
  bg: '#13141d',
  pointerBg: '#13141d',
  maskFill: MASK_FILL_DARK,
  summaryBackdrop: SUMMARY_BACKDROP_DARK,
  arrowStroke: ARROW_STROKE_DARK,
  arrowHalo: ARROW_HALO_DARK,
});

const TOOLTIP_THEME_LIGHT = Object.freeze({
  bg: '#ffffff',
  pointerBg: '#ffffff',
  maskFill: MASK_FILL_LIGHT,
  summaryBackdrop: SUMMARY_BACKDROP_DARK,
  arrowStroke: ARROW_STROKE_LIGHT,
  arrowHalo: ARROW_HALO_LIGHT,
});

export function readTooltipTheme() {
  if (typeof document === 'undefined') return TOOLTIP_THEME_DARK;
  const mode = document.documentElement.getAttribute('data-theme');
  return mode === 'light' ? TOOLTIP_THEME_LIGHT : TOOLTIP_THEME_DARK;
}

export function subscribeTooltipTheme(callback) {
  if (typeof document === 'undefined') return () => {};
  const observer = new MutationObserver(callback);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  return () => observer.disconnect();
}

export function getTooltipThemeServerSnapshot() {
  return TOOLTIP_THEME_DARK;
}
