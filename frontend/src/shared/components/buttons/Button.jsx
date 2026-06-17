import { forwardRef, useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import Spinner from '../feedback/Spinner';

const cx = (...parts) => parts.filter(Boolean).join(' ');

// `relative overflow-hidden` lets the press ripple stay clipped to the button shape; the focus ring + shadows are
// the element's own box-shadow so they are NOT clipped by overflow-hidden.
const BASE = 'relative overflow-hidden inline-flex items-center justify-center gap-1.5 font-display font-semibold transition-all duration-150 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed select-none focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 focus-visible:ring-offset-2 focus-visible:ring-offset-bg-base';

const VARIANTS = {
  primary: 'text-white bg-accent hover:bg-accent-bright border-none shadow-sm shadow-accent/20',
  secondary: 'text-fg-muted hover:text-fg bg-bg-elevated hover:bg-surface border border-border-default hover:border-border-hover',
  ghost: 'text-fg-muted hover:text-fg bg-transparent hover:bg-surface border-none',
  danger: 'text-danger hover:text-white bg-danger/10 hover:bg-danger border border-danger/30 hover:border-danger',
  gradient: 'text-white border-none shadow-sm shadow-accent/30 relative overflow-hidden bg-gradient-to-br from-accent via-accent-bright to-accent',
  segment: 'text-fg-muted hover:text-fg bg-transparent border-none relative',
  tab: 'text-fg-muted hover:text-fg bg-bg-elevated hover:bg-surface border border-border-default hover:border-border-hover relative',
  chip: 'text-fg-subtle hover:text-fg bg-transparent hover:bg-surface border border-border-default font-mono',
  warning: 'text-warning hover:text-white bg-warning/10 hover:bg-warning border border-warning/30 hover:border-warning',
  'warning-outline': 'text-warning hover:text-warning-bright bg-transparent border border-warning/30 hover:border-warning/60 hover:bg-warning/5',
};

const SIZES = {
  xs: 'px-2 py-1 text-[10px] rounded-md tracking-wide',
  sm: 'px-3 py-1.5 text-xs rounded-md',
  md: 'px-4 py-2 text-sm rounded-lg',
  lg: 'px-5 py-2.5 text-sm rounded-lg',
};

const SEGMENT_ACTIVE = 'text-accent';

// A snappy spring on press so buttons feel physical (a quick settle, not a linear shrink) — the light, premium
// micro-interaction the rest of the UI's motion language already uses, applied to every button by default.
const PRESS_SPRING = { type: 'spring', stiffness: 420, damping: 24, mass: 0.6 };
const MOTION_PRESETS = {
  tap: { whileTap: { scale: 0.96 }, transition: PRESS_SPRING },
  tapHover: { whileTap: { scale: 0.96 }, whileHover: { y: -1.5 }, transition: PRESS_SPRING },
  none: {},
};

const Button = forwardRef(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    leftIcon,
    rightIcon,
    fullWidth = false,
    motionPreset = 'tap',
    segmentActive = false,
    layoutId,
    accent,
    count,
    as,
    className,
    style,
    children,
    disabled,
    type = 'button',
    ...rest
  },
  ref,
) {
  const Component = as ?? motion.button;
  const motionProps = Component === motion.button ? MOTION_PRESETS[motionPreset] ?? MOTION_PRESETS.tap : {};
  const variantClass = VARIANTS[variant] ?? VARIANTS.primary;
  const sizeClass = SIZES[size] ?? SIZES.md;
  const inlineStyle = accent ? { ...(style || {}), '--accent-dynamic': accent } : style;

  // Minimal press ripple: spawn a short-lived circle at the pointer-down point; CSS animates + fades it, then it's
  // dropped from state. Skipped when disabled/loading. Composes with the caller's own onPointerDown.
  const [ripples, setRipples] = useState([]);
  const spawnRipple = useCallback((e) => {
    if (loading || disabled) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    const id = `${e.timeStamp}-${size}-${e.clientX}`;
    const x = e.clientX - rect.left - size / 2;
    const y = e.clientY - rect.top - size / 2;
    setRipples((rs) => [...rs, { id, x, y, size }]);
    window.setTimeout(() => setRipples((rs) => rs.filter((r) => r.id !== id)), 600);
  }, [loading, disabled]);
  const onPointerDown = (e) => { spawnRipple(e); rest.onPointerDown?.(e); };
  const restProps = { ...rest, onPointerDown };

  return (
    <Component
      ref={ref}
      type={Component === motion.button || Component === 'button' ? type : undefined}
      disabled={loading || disabled}
      aria-busy={loading || undefined}
      data-segment-active={variant === 'segment' && segmentActive ? '' : undefined}
      {...motionProps}
      className={cx(
        BASE,
        variantClass,
        sizeClass,
        fullWidth && 'w-full',
        variant === 'segment' && segmentActive && SEGMENT_ACTIVE,
        className,
      )}
      style={inlineStyle}
      {...restProps}
    >
      {ripples.map((r) => (
        <span
          key={r.id}
          aria-hidden="true"
          className="btn-ripple"
          style={{ left: r.x, top: r.y, width: r.size, height: r.size, opacity: 0.22 }}
        />
      ))}
      {variant === 'segment' && segmentActive && layoutId && (
        <motion.span
          layoutId={layoutId}
          className="absolute inset-0 rounded-md bg-accent/15"
          transition={{ type: 'spring', stiffness: 300, damping: 30 }}
        />
      )}
      {loading
        ? <Spinner size="sm" tone="inherit" className="relative" />
        : leftIcon && <span className="relative inline-flex shrink-0">{leftIcon}</span>}
      {children != null && <span className="relative inline-flex items-center gap-1.5">{children}</span>}
      {!loading && rightIcon && <span className="relative inline-flex shrink-0">{rightIcon}</span>}
      {count != null && (
        <span className="relative ml-1 font-mono text-[10px] text-fg-subtle tabular-nums">{count}</span>
      )}
    </Component>
  );
});

export default Button;
