import { motion, useReducedMotion } from 'framer-motion';
import { containerVariants, listItemVariants } from '../../utils/animations';

/**
 * A keyed container that re-fades-and-staggers its children whenever {@code reorderKey} changes — the one correct
 * way to animate a re-sorted / re-filtered list or table body without FLIP thrash. Remounting on a real key, not
 * per-row layout animation, is deliberate (it's the pattern BondsList proved out). The {@code reorderKey} slot
 * forces callers to pass a real string built from the sort/filter identity, which kills the whole class of
 * "a Set/object stringifies to [object Object] so the key never changes" bugs.
 *
 * Pair rows with {@link StaggerItem} (using {@code as="tr"} inside a {@code as="tbody"} list) for the cascade.
 * Honours prefers-reduced-motion by rendering a plain element.
 */
export function StaggerList({ reorderKey, stagger, className, children, as = 'div' }) {
  const reduce = useReducedMotion();
  if (reduce) {
    const Tag = as;
    return <Tag className={className}>{children}</Tag>;
  }
  const MotionTag = motion[as] || motion.div;
  return (
    <MotionTag key={reorderKey} variants={containerVariants(stagger)} initial="hidden" animate="show" className={className}>
      {children}
    </MotionTag>
  );
}

/** A single row/cell inside a {@link StaggerList}; cascades in via listItemVariants. */
export function StaggerItem({ className, children, as = 'div', ...rest }) {
  const reduce = useReducedMotion();
  if (reduce) {
    const Tag = as;
    return <Tag className={className} {...rest}>{children}</Tag>;
  }
  const MotionTag = motion[as] || motion.div;
  return (
    <MotionTag variants={listItemVariants} className={className} {...rest}>
      {children}
    </MotionTag>
  );
}

export default StaggerList;
