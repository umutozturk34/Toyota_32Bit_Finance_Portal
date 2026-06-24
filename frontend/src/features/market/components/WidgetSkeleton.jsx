import { useTranslation } from 'react-i18next';
import { Skeleton } from '../../../shared/components/feedback/Skeleton';

// The placeholder for a market-overview widget while it loads — the most-seen loader in the app. A faint header
// line + a few value rows so it reads as a materialising card, not a blank box. Built on the premium .skeleton.
export default function WidgetSkeleton() {
  const { t } = useTranslation();
  return (
    <div
      className="relative flex h-full flex-col gap-3 overflow-hidden rounded-xl border border-border-default/60 bg-bg-elevated/25 p-3.5"
      role="status"
      aria-busy="true"
      aria-label={t('common.loading')}
    >
      <div className="flex items-center gap-2.5">
        <Skeleton w="1.6rem" h="1.6rem" circle />
        <Skeleton w="45%" h="0.8rem" />
      </div>
      <div className="flex-1 space-y-2.5 pt-1">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex items-center justify-between gap-3">
            <Skeleton w="40%" h="0.7rem" />
            <Skeleton w="20%" h="0.7rem" />
          </div>
        ))}
      </div>
    </div>
  );
}
