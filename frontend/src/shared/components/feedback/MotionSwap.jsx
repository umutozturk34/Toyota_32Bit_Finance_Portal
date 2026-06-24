import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { landingVariants } from '../../utils/animations';

/**
 * Animates its children whenever {@code swapKey} changes — a light fade-and-lift "landing" for content that
 * swaps in place: a tab panel, a re-sorted or re-filtered list, a switched currency. `mode="wait"` lets the old
 * content drift out before the new one lands, so the change reads as one deliberate motion. `initial={false}`
 * keeps the very first render static (no entrance flash on mount). Honours prefers-reduced-motion by rendering
 * a plain container with no transition.
 *
 * Set {@code fade} to true for an opacity-only swap (no y-lift) — use this when the swapped content owns heavy,
 * kept-alive children (e.g. a chart canvas) that should not be jolted vertically.
 */
export default function MotionSwap({ swapKey, children, className, fade = false }) {
  const reduce = useReducedMotion();
  if (reduce) return <div className={className}>{children}</div>;
  const variants = fade
    ? {
        initial: { opacity: 0 },
        animate: { opacity: 1, transition: { duration: 0.22 } },
        exit: { opacity: 0, transition: { duration: 0.12 } },
      }
    : landingVariants;
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={swapKey}
        variants={variants}
        initial="initial"
        animate="animate"
        exit="exit"
        className={className}
      >
        {children}
      </motion.div>
    </AnimatePresence>
  );
}
