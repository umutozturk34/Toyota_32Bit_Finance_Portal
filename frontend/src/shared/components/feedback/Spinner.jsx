import i18n from '../../i18n/config';

const SIZE_CLASSES = {
  xs: 'h-2.5 w-2.5 border-[1.5px]',
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-8 w-8 border-[3px]',
};

const TONE_CLASSES = {
  accent: 'border-accent/25 border-t-accent',
  muted: 'border-fg-subtle/25 border-t-fg-muted',
  inherit: 'border-current/25 border-t-current',
};

const cx = (...parts) => parts.filter(Boolean).join(' ');

export default function Spinner({ size = 'sm', tone = 'accent', className, ...rest }) {
  return (
    <span
      role="status"
      aria-label={rest['aria-label'] ?? i18n.t('common.loading')}
      className={cx(
        'inline-block rounded-full animate-spin',
        SIZE_CLASSES[size] ?? SIZE_CLASSES.sm,
        TONE_CLASSES[tone] ?? TONE_CLASSES.accent,
        className,
      )}
    />
  );
}
