export default function WidgetSkeleton() {
  return (
    <div
      className="relative h-full overflow-hidden rounded-xl bg-bg-elevated/25 border border-border-default/60"
      role="status"
      aria-busy="true"
      aria-label="Loading"
    >
      <div className="pointer-events-none absolute inset-0 skeleton-sweep" aria-hidden="true" />
    </div>
  );
}
