import { motion } from 'framer-motion';
import { EASE_OUT_EXPO, RING_DRAW_MS, Z_MASK, Z_RING } from './constants';

function roundedRectPath(x, y, w, h, r) {
  const radius = Math.max(0, Math.min(r, w / 2, h / 2));
  return (
    `M ${x + radius},${y} ` +
    `H ${x + w - radius} ` +
    `A ${radius},${radius} 0 0 1 ${x + w},${y + radius} ` +
    `V ${y + h - radius} ` +
    `A ${radius},${radius} 0 0 1 ${x + w - radius},${y + h} ` +
    `H ${x + radius} ` +
    `A ${radius},${radius} 0 0 1 ${x},${y + h - radius} ` +
    `V ${y + radius} ` +
    `A ${radius},${radius} 0 0 1 ${x + radius},${y} ` +
    'Z'
  );
}

export function SpotlightMask({ rect, padding, viewportW, viewportH, fill }) {
  const x = rect ? Math.max(0, rect.left - padding) : 0;
  const y = rect ? Math.max(0, rect.top - padding) : 0;
  const w = rect ? rect.width + padding * 2 : 0;
  const h = rect ? rect.height + padding * 2 : 0;
  return (
    <svg
      width={viewportW}
      height={viewportH}
      className="pointer-events-auto"
      style={{ position: 'fixed', top: 0, left: 0, zIndex: Z_MASK }}
      aria-hidden="true"
    >
      <defs>
        <mask id="tour-spotlight-mask">
          <rect width={viewportW} height={viewportH} fill="white" />
          {rect && (
            // The dark cutout glides between steps (animated x/y/w/h) so the spotlight slides to the next target
            // instead of teleporting — the single biggest "this tour feels crafted" cue across a long flow.
            <motion.rect
              initial={false}
              animate={{ x, y, width: w, height: h }}
              transition={{ type: 'spring', stiffness: 260, damping: 30 }}
              rx={14}
              ry={14}
              fill="black"
            />
          )}
        </mask>
      </defs>
      <rect
        width={viewportW}
        height={viewportH}
        fill={fill}
        mask="url(#tour-spotlight-mask)"
      />
    </svg>
  );
}

export function SpotlightRing({ rect, padding, viewportW, viewportH }) {
  if (!rect) return null;
  const x = Math.max(0, rect.left - padding);
  const y = Math.max(0, rect.top - padding);
  const w = rect.width + padding * 2;
  const h = rect.height + padding * 2;
  const ringPath = roundedRectPath(x, y, w, h, 14);
  return (
    <>
      <motion.div
        aria-hidden="true"
        className="pointer-events-none"
        initial={{ opacity: 0 }}
        // Position (top/left/width/height) tweens on a spring so the glow box GLIDES to the next target in lockstep
        // with the mask cutout; the scale + boxShadow keep their gentle breathing pulse independently.
        animate={{
          opacity: 1,
          top: y,
          left: x,
          width: w,
          height: h,
          scale: [1, 1.04, 1],
          boxShadow: [
            '0 0 0 1px rgba(99,102,241,0.16), 0 0 22px 2px rgba(99,102,241,0.18)',
            '0 0 0 1px rgba(99,102,241,0.28), 0 0 40px 6px rgba(99,102,241,0.32)',
            '0 0 0 1px rgba(99,102,241,0.16), 0 0 22px 2px rgba(99,102,241,0.18)',
          ],
        }}
        transition={{
          opacity: { duration: 0.32, ease: EASE_OUT_EXPO },
          top: { type: 'spring', stiffness: 260, damping: 30 },
          left: { type: 'spring', stiffness: 260, damping: 30 },
          width: { type: 'spring', stiffness: 260, damping: 30 },
          height: { type: 'spring', stiffness: 260, damping: 30 },
          scale: { duration: 2, ease: 'easeInOut', repeat: Infinity, delay: RING_DRAW_MS / 1000 },
          boxShadow: { duration: 2, ease: 'easeInOut', repeat: Infinity, delay: RING_DRAW_MS / 1000 },
        }}
        style={{
          position: 'fixed',
          borderRadius: 14,
          zIndex: Z_RING,
          willChange: 'transform, box-shadow',
        }}
      />
      <svg
        width={viewportW}
        height={viewportH}
        className="pointer-events-none"
        style={{ position: 'fixed', top: 0, left: 0, zIndex: Z_RING }}
        aria-hidden="true"
      >
        {/* The crisp stroke draws itself in on first appearance (pathLength), then MORPHS its `d` between steps —
            both rounded-rect paths share the same command structure, so framer interpolates them smoothly. */}
        <motion.path
          fill="none"
          stroke="var(--color-accent)"
          strokeWidth="1.5"
          strokeLinecap="round"
          initial={{ pathLength: 0, opacity: 0 }}
          animate={{ pathLength: 1, opacity: 1, d: ringPath }}
          transition={{
            pathLength: { duration: RING_DRAW_MS / 1000, ease: EASE_OUT_EXPO },
            opacity: { duration: 0.2, ease: 'easeOut' },
            d: { type: 'spring', stiffness: 260, damping: 30 },
          }}
        />
      </svg>
    </>
  );
}
