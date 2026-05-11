import { ShoppingCart } from '../feedback/AnimatedIcons';
import { useTranslation } from 'react-i18next';
import IconButton from '../buttons/IconButton';

export default function AssetBuyButton({ onClick, title }) {
  const { t } = useTranslation();
  const tooltip = title ?? t('common.addToPortfolio');
  return (
    <IconButton
      variant="secondary"
      size={7}
      shape="square"
      icon={<ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />}
      aria-label={tooltip}
      title={tooltip}
      onClick={(e) => { e.stopPropagation(); onClick(e); }}
    />
  );
}
