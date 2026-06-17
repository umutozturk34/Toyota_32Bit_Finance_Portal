import { AnimatePresence, motion } from 'framer-motion';
import { landingVariants } from '../../utils/animations';

/**
 * Animates its children whenever {@code swapKey} changes — a light fade-and-lift "landing" for content that
 * swaps in place: a tab panel, a re-sorted or re-filtered list, a switched currency. `mode="wait"` lets the old
 * content drift out before the new one lands, so the change reads as one deliberate motion. `initial={false}`
 * keeps the very first render static (no entrance flash on mount).
 */
export default function MotionSwap({ swapKey, children, className }) {
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={swapKey}
        variants={landingVariants}
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
