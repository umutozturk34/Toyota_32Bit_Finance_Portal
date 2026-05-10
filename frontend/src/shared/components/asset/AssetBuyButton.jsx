import { ShoppingCart } from '../feedback/AnimatedIcons';
import { useTranslation } from 'react-i18next';

export default function AssetBuyButton({ onClick, title }) {
  const { t } = useTranslation();
  const tooltip = title ?? t('common.addToPortfolio');
  return (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onClick(e);
      }}
      title={tooltip}
      className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
    >
      <ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />
    </button>
  );
}
