import { X } from 'lucide-react';
import { Z_TOP_SKIP } from './constants';

export default function SkipButton({ t, onSkip }) {
  return (
    <button
      type="button"
      onClick={onSkip}
      aria-label={t('onboarding.skip')}
      className="fixed top-3 right-3 sm:top-5 sm:right-5 inline-flex items-center justify-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated/95 min-w-[40px] min-h-[40px] sm:min-w-0 sm:min-h-0 sm:px-2.5 sm:py-1.5 text-[11px] text-fg-muted backdrop-blur-md transition-colors hover:text-fg hover:border-accent/40 cursor-pointer"
      style={{ zIndex: Z_TOP_SKIP }}
    >
      <X className="h-4 w-4 sm:h-3 sm:w-3" />
      <span className="hidden sm:inline">{t('onboarding.skip')}</span>
    </button>
  );
}
