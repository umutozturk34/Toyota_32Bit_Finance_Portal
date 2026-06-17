export const TOOLTIP_MAX_WIDTH = 360;
export const TOOLTIP_MIN_WIDTH = 280;
export const TOOLTIP_SUMMARY_WIDTH = 560;
export const TOOLTIP_SIDE_MARGIN = 12;
export const TOOLTIP_OFFSET = 22;
export const RECT_CUSHION = 6;
export const DEFAULT_PADDING = 14;
export const POLL_MAX_FRAMES = 20;
export const POLL_MAX_FRAMES_SLOW = 36;
// Frames to wait for a found target's rect to stop moving (smooth scroll-into-view settling, or a drawer's
// slide-in) before the spotlight commits to it. ~72 frames ≈ 1.2s covers a smooth scroll plus the longest drawer
// animation; a static, in-view target settles in two frames and exits immediately.
export const SETTLE_MAX_FRAMES = 72;
export const ROUTE_SETTLE_MS = 420;
export const ACTION_SETTLE_MS = 240;
export const CLOSE_SETTLE_MS = 180;
export const MOBILE_BREAKPOINT = 640;
export const TABLET_BREAKPOINT = 1024;
export const Z_OVERLAY = 2147483640;
export const Z_MASK = 1;
export const Z_RING = 2;
export const Z_ARROW = 4;
export const Z_TOOLTIP = 5;
export const Z_TOP_SKIP = 6;
export const TRANSITION_MS = 280;
export const RING_DRAW_MS = 520;
export const ARROW_DELAY_MS = RING_DRAW_MS + 150;
export const ARROW_DRAW_MS = 420;
export const ARROW_STROKE_DARK = '#c4b5fd';
export const ARROW_STROKE_LIGHT = '#6366f1';
export const ARROW_HALO_DARK = 'drop-shadow(0 0 8px rgba(167,139,250,0.65))';
export const ARROW_HALO_LIGHT = 'drop-shadow(0 0 6px rgba(99,102,241,0.45))';
export const MASK_FILL_DARK = 'rgba(2, 6, 23, 0.74)';
export const MASK_FILL_LIGHT = 'rgba(15, 23, 42, 0.40)';
export const SUMMARY_BACKDROP_DARK = 'rgba(2, 6, 23, 0.82)';
export const SUMMARY_BACKDROP_LIGHT = 'rgba(15, 23, 42, 0.45)';
export const EASE_OUT_EXPO = [0.22, 1, 0.36, 1];
