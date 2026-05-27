import { motion } from 'framer-motion';
import { ARROW_DELAY_MS, ARROW_DRAW_MS, EASE_OUT_EXPO, Z_ARROW } from './constants';

export default function ArrowConnector({ path, viewportW, viewportH, stroke, halo }) {
  if (!path) return null;
  const angleDeg = (Math.atan2(path.endY - path.startY, path.endX - path.startX) * 180) / Math.PI;
  const arrowDelay = ARROW_DELAY_MS / 1000;
  const headDelay = arrowDelay + ARROW_DRAW_MS / 1000;
  return (
    <svg
      width={viewportW}
      height={viewportH}
      className="pointer-events-none"
      style={{ position: 'fixed', top: 0, left: 0, zIndex: Z_ARROW, filter: halo }}
      aria-hidden="true"
    >
      <motion.line
        x1={path.startX}
        y1={path.startY}
        x2={path.endX}
        y2={path.endY}
        stroke={stroke}
        strokeWidth="2.5"
        strokeLinecap="round"
        initial={{ pathLength: 0, opacity: 0 }}
        animate={{ pathLength: 1, opacity: 1 }}
        transition={{
          pathLength: { duration: ARROW_DRAW_MS / 1000, ease: EASE_OUT_EXPO, delay: arrowDelay },
          opacity: { duration: 0.18, ease: 'easeOut', delay: arrowDelay },
        }}
      />
      <motion.path
        d="M -8,-5 L 0,0 L -8,5 Z"
        fill={stroke}
        transform={`translate(${path.endX} ${path.endY}) rotate(${angleDeg})`}
        initial={{ scale: 0, opacity: 0 }}
        animate={{ scale: [0, 1.2, 1], opacity: 1 }}
        transition={{
          scale: { duration: 0.32, ease: EASE_OUT_EXPO, delay: headDelay, times: [0, 0.6, 1] },
          opacity: { duration: 0.15, ease: 'easeOut', delay: headDelay },
        }}
        style={{ transformOrigin: 'center', transformBox: 'fill-box' }}
      />
    </svg>
  );
}
