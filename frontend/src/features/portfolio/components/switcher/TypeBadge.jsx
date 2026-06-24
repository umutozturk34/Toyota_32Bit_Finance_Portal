import { useTranslation } from 'react-i18next';
import { PORTFOLIO_TYPES } from './portfolioTypes';

export default function TypeBadge({ type }) {
  const { t } = useTranslation();
  const meta = PORTFOLIO_TYPES.find((x) => x.id === type) ?? PORTFOLIO_TYPES[0];
  const Icon = meta.Icon;
  return (
    <span className="inline-flex items-center gap-1 shrink-0 rounded-md bg-bg-base/70 px-1.5 py-0.5 text-[10px] font-medium text-fg-muted ring-1 ring-inset ring-border-default/60">
      <Icon className="h-3 w-3" />
      <span className="hidden sm:inline">{t(meta.labelKey)}</span>
    </span>
  );
}
