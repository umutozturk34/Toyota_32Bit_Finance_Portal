import { motion } from 'framer-motion';

const cx = (...parts) => parts.filter(Boolean).join(' ');

// The shared placeholder surface: a visible tonal block with a soft sheen gliding across (theme-aware .skeleton).
// One look, used everywhere, so loading states read as a coherent "content is materialising" beat, not a blank gap.
const BASE = 'skeleton';

/**
 * A single skeleton block. Pass {@code w}/{@code h} (any CSS length) or Tailwind classes via {@code className};
 * {@code circle} rounds it fully (avatars/logos). Inline width/height keep it usable inside flex/grid cells.
 */
export function Skeleton({ w, h, circle = false, className, style }) {
  return (
    <span
      aria-hidden="true"
      className={cx(BASE, circle ? 'rounded-full' : 'rounded-lg', 'block', className)}
      style={{ width: w, height: h, ...style }}
    />
  );
}

/** A stack of text lines; the last line is shortened so it reads like a real paragraph tail. */
export function SkeletonText({ lines = 3, className }) {
  return (
    <div className={cx('space-y-2', className)} aria-hidden="true">
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} h="0.7rem" className={i === lines - 1 ? 'w-2/3' : 'w-full'} />
      ))}
    </div>
  );
}

/** A card placeholder: cover image strip, a category chip, a two-line title and a meta row — matches news/asset cards. */
export function SkeletonCard({ image = true, className }) {
  return (
    <div
      aria-hidden="true"
      className={cx('overflow-hidden rounded-2xl border border-border-default bg-bg-elevated/60', className)}
    >
      {image && <Skeleton h="9.5rem" className="rounded-none" />}
      <div className="space-y-3 p-4">
        <Skeleton w="5rem" h="1.1rem" className="rounded-md" />
        <div className="space-y-2">
          <Skeleton h="0.85rem" className="w-full" />
          <Skeleton h="0.85rem" className="w-4/5" />
        </div>
        <div className="flex items-center gap-2 pt-1">
          <Skeleton w="4rem" h="0.7rem" />
          <Skeleton w="3rem" h="0.7rem" />
        </div>
      </div>
    </div>
  );
}

/** A table-row placeholder: leading avatar + a label stack, then {@code cols} trailing value cells. */
export function SkeletonRow({ cols = 4, avatar = true, className }) {
  return (
    <div
      aria-hidden="true"
      className={cx('flex items-center gap-3 border-b border-border-default px-4 py-3 last:border-b-0', className)}
    >
      {avatar && <Skeleton w="2rem" h="2rem" circle className="shrink-0" />}
      <div className="min-w-0 flex-1 space-y-1.5">
        <Skeleton w="40%" h="0.8rem" />
        <Skeleton w="25%" h="0.6rem" />
      </div>
      {Array.from({ length: cols }).map((_, i) => (
        <Skeleton key={i} w="3.5rem" h="0.8rem" className="hidden sm:block" />
      ))}
    </div>
  );
}

/** A chart placeholder: a tonal panel with faint baseline gridlines under the sweep, sized by {@code h}. */
export function SkeletonChart({ h = '20rem', className }) {
  return (
    <div
      aria-hidden="true"
      className={cx(BASE, 'rounded-2xl border border-border-default', className)}
      style={{ height: h }}
    >
      <div className="absolute inset-0 flex flex-col justify-between p-6 opacity-40">
        {Array.from({ length: 5 }).map((_, i) => (
          <span key={i} className="h-px w-full bg-border-default" />
        ))}
      </div>
    </div>
  );
}

/** A compact metric-tile placeholder: small label line over a larger value line. */
export function SkeletonStat({ className }) {
  return (
    <div
      aria-hidden="true"
      className={cx('space-y-2 rounded-xl border border-border-default bg-bg-elevated/60 p-4', className)}
    >
      <Skeleton w="55%" h="0.65rem" />
      <Skeleton w="75%" h="1.4rem" className="rounded-md" />
    </div>
  );
}

/**
 * A responsive grid of {@link SkeletonCard}s with a soft staggered fade so the placeholders themselves arrive
 * gracefully (not all at once). Used while a card grid — news, market, watchlists — is still loading.
 */
export function SkeletonCardGrid({ count = 8, image = true, className }) {
  return (
    <div className={cx('grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4', className)}>
      {Array.from({ length: count }).map((_, i) => (
        <motion.div
          key={i}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: Math.min(i * 0.04, 0.4), duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
        >
          <SkeletonCard image={image} />
        </motion.div>
      ))}
    </div>
  );
}

/** A list of {@link SkeletonRow}s inside a bordered panel — for table/list views (positions, returns, admin). */
export function SkeletonList({ rows = 6, cols = 4, avatar = true, className }) {
  return (
    <div className={cx('overflow-hidden rounded-2xl border border-border-default bg-bg-elevated/50', className)}>
      {Array.from({ length: rows }).map((_, i) => (
        <SkeletonRow key={i} cols={cols} avatar={avatar} />
      ))}
    </div>
  );
}

export default Skeleton;
