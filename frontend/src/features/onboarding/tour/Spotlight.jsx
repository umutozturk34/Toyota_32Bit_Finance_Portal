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
            // The cutout repositions instantly to the new target — never glides across the viewport between steps.
            // The page itself smooth-scrolls the target into view (see useTourTarget), so the spotlight simply
            // reveals where the user is already looking instead of a circle flying across the screen.
            <rect x={x} y={y} width={w} height={h} rx={14} ry={14} fill="black" />
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
        // The glow box appears AT the target directly (position is static below) and only fades in + keeps its
        // gentle breathing pulse — it never glides across the screen between steps. Remounting per step (key in
        // ProductTour) replays the fade so each target gets a fresh, in-place reveal.
        animate={{
          opacity: 1,
          scale: [1, 1.04, 1],
          boxShadow: [
            '0 0 0 1px rgba(99,102,241,0.16), 0 0 22px 2px rgba(99,102,241,0.18)',
            '0 0 0 1px rgba(99,102,241,0.28), 0 0 40px 6px rgba(99,102,241,0.32)',
            '0 0 0 1px rgba(99,102,241,0.16), 0 0 22px 2px rgba(99,102,241,0.18)',
          ],
        }}
        transition={{
          opacity: { duration: 0.32, ease: EASE_OUT_EXPO },
          scale: { duration: 2, ease: 'easeInOut', repeat: Infinity, delay: RING_DRAW_MS / 1000 },
          boxShadow: { duration: 2, ease: 'easeInOut', repeat: Infinity, delay: RING_DRAW_MS / 1000 },
        }}
        style={{
          position: 'fixed',
          top: y,
          left: x,
          width: w,
          height: h,
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
        {/* The crisp stroke draws itself in on appearance (pathLength). `d` is applied directly so the ring is
            placed at the new target instantly — no morphing/gliding across the viewport between steps. */}
        <motion.path
          fill="none"
          stroke="var(--color-accent)"
          strokeWidth="1.5"
          strokeLinecap="round"
          d={ringPath}
          initial={{ pathLength: 0, opacity: 0 }}
          animate={{ pathLength: 1, opacity: 1 }}
          transition={{
            pathLength: { duration: RING_DRAW_MS / 1000, ease: EASE_OUT_EXPO },
            opacity: { duration: 0.2, ease: 'easeOut' },
          }}
        />
      </svg>
    </>
  );
}
