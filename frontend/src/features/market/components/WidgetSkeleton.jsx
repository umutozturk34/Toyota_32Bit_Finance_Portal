import { useTranslation } from 'react-i18next';

export default function WidgetSkeleton() {
  const { t } = useTranslation();
  return (
    <div
      className="relative h-full overflow-hidden rounded-xl bg-bg-elevated/25 border border-border-default/60"
      role="status"
      aria-busy="true"
      aria-label={t('common.loading')}
    >
      <div className="pointer-events-none absolute inset-0 skeleton-sweep" aria-hidden="true" />
    </div>
  );
}
