import { cardVariants } from '../../utils/animations';

export default function AssetCard({ onClick, size = 'lg', children, className = '' }) {
  const padding = size === 'sm' ? 'p-4' : 'p-5';
  const rounded = size === 'sm' ? 'rounded-xl' : 'rounded-2xl';
  return (
    <motion.div
      variants={cardVariants}
      onClick={onClick}
      className={`group cursor-pointer ${rounded} border border-border-default bg-bg-elevated ${padding} card-hover transition-all duration-200 hover:border-border-hover ${className}`}
    >
      {children}
    </motion.div>
  );
}
