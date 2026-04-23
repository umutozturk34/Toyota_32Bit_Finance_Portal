import { ShoppingCart } from './AnimatedIcons';

export default function AssetBuyButton({ onClick, title = 'Satın Al' }) {
  return (
    <button
      onClick={(e) => {
        e.stopPropagation();
        onClick(e);
      }}
      title={title}
      className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base transition-colors duration-150 hover:bg-surface"
    >
      <ShoppingCart className="h-3.5 w-3.5 text-fg-subtle group-hover:text-success transition-colors duration-150" />
    </button>
  );
}
