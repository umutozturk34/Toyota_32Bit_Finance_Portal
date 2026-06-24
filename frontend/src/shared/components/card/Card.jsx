import { forwardRef } from 'react';

const cx = (...parts) => parts.filter(Boolean).join(' ');

const VARIANTS = {
  elevated: 'border border-border-default bg-bg-elevated',
  glass: 'border border-border-default glass',
  surface: 'border border-border-default bg-surface',
  gradient: 'border border-border-default',
  outline: 'border border-border-default bg-transparent',
  popover: 'border border-accent/30 bg-bg-deep/95 shadow-2xl',
  placeholder: 'border border-dashed border-border-default/60 bg-bg-elevated/40',
};

const TONES = {
  default: '',
  success: 'border-success/30 bg-success/5',
  danger: 'border-danger/30 bg-danger/5',
  warning: 'border-warning/30 bg-warning/5',
  accent: 'border-accent/30 bg-accent/5',
  gradient: '',
};

const ACCENT_BAR_TOKENS = {
  accent: 'border-t-accent',
  'accent-secondary': 'border-t-accent-secondary',
  success: 'border-t-success',
  danger: 'border-t-danger',
  warning: 'border-t-warning',
};

const RADII = {
  md: 'rounded-md',
  lg: 'rounded-lg',
  xl: 'rounded-xl',
  '2xl': 'rounded-2xl',
  '3xl': 'rounded-3xl',
};

const PADDINGS = {
  none: '',
  sm: 'p-3',
  md: 'p-4',
  lg: 'p-5',
  xl: 'p-8',
};

const GRADIENT_TOKEN_MAP = {
  accent: 'rgba(99, 102, 241, 0.08)',
  'accent-secondary': 'rgba(139, 92, 246, 0.06)',
  success: 'rgba(16, 185, 129, 0.06)',
  danger: 'rgba(239, 68, 68, 0.06)',
  warning: 'rgba(245, 158, 11, 0.06)',
};

const resolveGradientColor = (token) => {
  if (!token) return null;
  if (token.startsWith('#') || token.startsWith('rgb') || token.startsWith('hsl')) return token;
  return GRADIENT_TOKEN_MAP[token] ?? null;
};

const resolveAccentBar = (accentBar) => {
  if (!accentBar || accentBar === 'none') return { className: null, style: null };
  if (accentBar in ACCENT_BAR_TOKENS) {
    return { className: cx('border-t-2', ACCENT_BAR_TOKENS[accentBar]), style: null };
  }
  return { className: 'border-t-2', style: { borderTopColor: accentBar } };
};

const Card = forwardRef(function Card(
  {
    variant = 'elevated',
    tone = 'default',
    accentBar = 'none',
    radius = 'xl',
    padding = 'md',
    interactive = false,
    hoverable = true,
    backdropBlur = false,
    pending = false,
    clip = true,
    gradientFrom,
    gradientTo,
    as,
    className,
    style,
    children,
    ...rest
  },
  ref,
) {
  const Component = as ?? 'div';
  const variantClass = VARIANTS[variant] ?? VARIANTS.elevated;
  const toneClass = TONES[tone] ?? TONES.default;
  const radiusClass = RADII[radius] ?? RADII.xl;
  const paddingClass = PADDINGS[padding] ?? PADDINGS.md;
  const accent = resolveAccentBar(accentBar);

  const gradientStyle = (() => {
    if (tone !== 'gradient' && variant !== 'gradient') return null;
    const fromColor = resolveGradientColor(gradientFrom);
    const toColor = resolveGradientColor(gradientTo);
    if (!fromColor && !toColor) return null;
    return {
      backgroundImage: `linear-gradient(135deg, ${fromColor ?? 'transparent'}, ${toColor ?? 'transparent'})`,
    };
  })();

  const composedStyle = {
    ...(style || {}),
    ...(accent.style || {}),
    ...(gradientStyle || {}),
  };

  return (
    <Component
      ref={ref}
      className={cx(
        'relative',
        // Cards clip to their rounded corners by default. Set clip={false} when a child needs to overflow
        // the card bounds — e.g. a search-results / date-picker dropdown that opens downward past the card
        // edge would otherwise be cut off ("crushed") by overflow-hidden.
        clip ? 'overflow-hidden' : 'overflow-visible',
        variantClass,
        toneClass,
        radiusClass,
        paddingClass,
        accent.className,
        interactive && 'card-hover cursor-pointer hover:border-border-hover transition-all duration-200',
        !interactive && hoverable && (variant === 'elevated' || variant === 'glass') && 'card-hover transition-all duration-200',
        backdropBlur && 'backdrop-blur-md',
        pending && 'opacity-60 pointer-events-none border-accent/40',
        className,
      )}
      style={composedStyle}
      {...rest}
    >
      {children}
    </Component>
  );
});

export default Card;
