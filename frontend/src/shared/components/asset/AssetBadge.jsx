import { ASSET_TYPE_STYLES } from '../../constants/assetTypes';

export default function AssetBadge({ assetType, assetCode, assetImage, size = 'md' }) {
  const dims = size === 'sm' ? 'w-7 h-7 text-xs' : 'w-9 h-9 text-sm';
  const typeStyle = ASSET_TYPE_STYLES[assetType] || ASSET_TYPE_STYLES.CRYPTO;
  if (assetImage) {
    return (
      <img
        src={assetImage}
        alt={assetCode}
        className={`${dims} rounded-lg shrink-0 object-cover`}
      />
    );
  }
  return (
    <span
      className={`flex items-center justify-center ${dims} rounded-lg ${typeStyle.bg} font-bold ${typeStyle.text} shrink-0`}
    >
      {(assetCode ?? '?').slice(0, 3).toUpperCase()}
    </span>
  );
}
