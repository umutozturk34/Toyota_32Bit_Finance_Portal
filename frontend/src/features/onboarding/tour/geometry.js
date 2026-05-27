import {
  MOBILE_BREAKPOINT,
  SIDEBAR_PLACEMENTS,
  TABLET_BREAKPOINT,
  TOOLTIP_MAX_WIDTH,
  TOOLTIP_MIN_WIDTH,
  TOOLTIP_OFFSET,
  TOOLTIP_SIDE_MARGIN,
  TOOLTIP_SUMMARY_WIDTH,
} from './constants';

export function tooltipEnterOffset(placement) {
  if (placement === 'bottom') return { x: 0, y: 14 };
  if (placement === 'top') return { x: 0, y: -14 };
  if (placement === 'right') return { x: 14, y: 0 };
  if (placement === 'left') return { x: -14, y: 0 };
  return { x: 0, y: 8 };
}

export function tooltipExitOffset(placement) {
  if (placement === 'bottom') return { x: 0, y: -8 };
  if (placement === 'top') return { x: 0, y: 8 };
  if (placement === 'right') return { x: -8, y: 0 };
  if (placement === 'left') return { x: 8, y: 0 };
  return { x: 0, y: -6 };
}

export function clamp(n, min, max) {
  return Math.max(min, Math.min(max, n));
}

export function resolveTooltipWidth(viewportW, isSummary) {
  if (isSummary) {
    const cap = viewportW - TOOLTIP_SIDE_MARGIN * 2;
    return Math.min(TOOLTIP_SUMMARY_WIDTH, Math.max(TOOLTIP_MIN_WIDTH, cap));
  }
  return Math.min(TOOLTIP_MAX_WIDTH, Math.max(TOOLTIP_MIN_WIDTH, viewportW - TOOLTIP_SIDE_MARGIN * 2));
}

export function computeTooltipPosition(rect, placement, viewportW, viewportH, isSummary, tooltipH) {
  const width = resolveTooltipWidth(viewportW, isSummary);
  const isMobile = viewportW < MOBILE_BREAKPOINT;
  const isTablet = viewportW < TABLET_BREAKPOINT;
  const isSidebarPlacement = SIDEBAR_PLACEMENTS.includes(placement);
  const h = Math.max(160, tooltipH || 180);

  if (isSummary) {
    return {
      left: viewportW / 2 - width / 2,
      top: Math.max(TOOLTIP_SIDE_MARGIN, viewportH / 2 - 220),
      placement: 'center',
      width,
    };
  }

  if (isTablet && isSidebarPlacement) {
    return {
      left: viewportW / 2 - width / 2,
      top: Math.max(TOOLTIP_SIDE_MARGIN, viewportH / 2 - h / 2),
      placement: 'center',
      width,
    };
  }

  if (!rect || placement === 'center' || isMobile) {
    if (isMobile && rect) {
      const belowTop = Math.min(rect.bottom + TOOLTIP_OFFSET, viewportH - h - 20);
      return { left: TOOLTIP_SIDE_MARGIN, top: Math.max(TOOLTIP_SIDE_MARGIN, belowTop), placement: 'mobile', width };
    }
    return {
      left: viewportW / 2 - width / 2,
      top: Math.max(TOOLTIP_SIDE_MARGIN, viewportH / 2 - h / 2),
      placement: 'center',
      width,
    };
  }

  const spaces = {
    top: rect.top,
    bottom: viewportH - rect.bottom,
    left: rect.left,
    right: viewportW - rect.right,
  };

  const order = placement === 'auto'
    ? Object.entries(spaces).sort((a, b) => b[1] - a[1]).map(([k]) => k)
    : [placement, 'bottom', 'top', 'right', 'left'];

  const needV = h + TOOLTIP_OFFSET + 12;
  const SKIP_SAFE_TOP = 64;
  const SKIP_SAFE_RIGHT_ZONE = 180;
  const avoidSkip = (top, left) => {
    const tooltipRight = left + width;
    if (top < SKIP_SAFE_TOP && tooltipRight > viewportW - SKIP_SAFE_RIGHT_ZONE) {
      return { top: SKIP_SAFE_TOP, left };
    }
    return { top, left };
  };

  for (const p of order) {
    if (p === 'bottom' && spaces.bottom >= needV) {
      const left = clamp(rect.left + rect.width / 2 - width / 2, 12, viewportW - width - 12);
      const safe = avoidSkip(rect.bottom + TOOLTIP_OFFSET, left);
      return { left: safe.left, top: safe.top, placement: 'bottom', width };
    }
    if (p === 'top' && spaces.top >= needV) {
      const left = clamp(rect.left + rect.width / 2 - width / 2, 12, viewportW - width - 12);
      const safe = avoidSkip(rect.top - TOOLTIP_OFFSET - h, left);
      return { left: safe.left, top: safe.top, placement: 'top', width };
    }
    if (p === 'right' && spaces.right >= width + 24) {
      const top = clamp(rect.top + rect.height / 2 - h / 2, 12, viewportH - h - 12);
      const safe = avoidSkip(top, rect.right + TOOLTIP_OFFSET);
      return { left: safe.left, top: safe.top, placement: 'right', width };
    }
    if (p === 'left' && spaces.left >= width + 24) {
      const top = clamp(rect.top + rect.height / 2 - h / 2, 12, viewportH - h - 12);
      const safe = avoidSkip(top, rect.left - TOOLTIP_OFFSET - width);
      return { left: safe.left, top: safe.top, placement: 'left', width };
    }
  }

  return {
    left: viewportW / 2 - width / 2,
    top: Math.max(TOOLTIP_SIDE_MARGIN, viewportH / 2 - h / 2),
    placement: 'center',
    width,
  };
}

export function buildArrowPath(spotlightRect, tooltipBox, placement) {
  if (!spotlightRect || !tooltipBox || placement === 'center' || placement === 'mobile') return null;

  let startX;
  let startY;
  let endX;
  let endY;

  if (placement === 'bottom') {
    startX = tooltipBox.left + tooltipBox.width / 2;
    startY = tooltipBox.top - 2;
    endX = spotlightRect.left + spotlightRect.width / 2;
    endY = spotlightRect.bottom + 4;
  } else if (placement === 'top') {
    startX = tooltipBox.left + tooltipBox.width / 2;
    startY = tooltipBox.top + tooltipBox.height + 2;
    endX = spotlightRect.left + spotlightRect.width / 2;
    endY = spotlightRect.top - 4;
  } else if (placement === 'right') {
    startX = tooltipBox.left - 2;
    startY = tooltipBox.top + tooltipBox.height / 2;
    endX = spotlightRect.right + 4;
    endY = spotlightRect.top + spotlightRect.height / 2;
  } else if (placement === 'left') {
    startX = tooltipBox.left + tooltipBox.width + 2;
    startY = tooltipBox.top + tooltipBox.height / 2;
    endX = spotlightRect.left - 4;
    endY = spotlightRect.top + spotlightRect.height / 2;
  } else {
    return null;
  }

  const dx = endX - startX;
  const dy = endY - startY;
  const len = Math.hypot(dx, dy);
  if (len < 12) return null;

  return { startX, startY, endX, endY };
}
