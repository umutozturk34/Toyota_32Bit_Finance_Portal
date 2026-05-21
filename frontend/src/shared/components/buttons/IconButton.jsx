import { forwardRef } from 'react';
import { motion } from 'framer-motion';
import Spinner from '../feedback/Spinner';

const cx = (...parts) => parts.filter(Boolean).join(' ');

const BASE = 'inline-flex items-center justify-center transition-all duration-150 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed select-none focus:outline-none focus-visible:ring-2 focus-visible:ring-accent/60 focus-visible:ring-offset-2 focus-visible:ring-offset-bg-base border-none bg-transparent';

const VARIANTS = {
  primary: 'text-white bg-accent hover:bg-accent-bright',
  secondary: 'text-fg-muted hover:text-fg bg-bg-elevated hover:bg-surface border border-border-default hover:border-border-hover',
  ghost: 'text-fg-muted hover:text-fg hover:bg-surface',
  danger: 'text-danger hover:text-white hover:bg-danger',
  accent: 'text-accent hover:text-accent-bright bg-accent/10 hover:bg-accent/20',
  warning: 'text-warning hover:text-warning-bright bg-warning/10 hover:bg-warning/20',
};

const SIZE_BOX = {
  7: 'w-7 h-7',
  8: 'w-8 h-8',
  9: 'w-9 h-9',
  10: 'w-10 h-10',
};

const SHAPES = {
  square: 'rounded-md',
  round: 'rounded-full',
};

const IconButton = forwardRef(function IconButton(
  {
    variant = 'ghost',
    size = 8,
    shape = 'square',
    icon,
    loading = false,
    as,
    className,
    style,
    disabled,
    type = 'button',
    ...rest
  },
  ref,
) {
  const Component = as ?? motion.button;
  const variantClass = VARIANTS[variant] ?? VARIANTS.ghost;
  const boxClass = SIZE_BOX[size] ?? SIZE_BOX[8];
  const shapeClass = SHAPES[shape] ?? SHAPES.square;

  return (
    <Component
      ref={ref}
      type={Component === motion.button || Component === 'button' ? type : undefined}
      disabled={loading || disabled}
      aria-busy={loading || undefined}
      whileTap={Component === motion.button ? { scale: 0.94 } : undefined}
      className={cx(BASE, variantClass, boxClass, shapeClass, className)}
      style={style}
      {...rest}
    >
      {loading ? <Spinner size="xs" tone="inherit" /> : icon}
    </Component>
  );
});

export default IconButton;
