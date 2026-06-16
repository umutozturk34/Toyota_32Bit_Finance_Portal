import { assetColorStyle } from '../../utils/assetColor';

export default function AssetBadge({ assetCode, assetImage, size = 'md' }) {
  const dims = size === 'sm' ? 'w-7 h-7 text-xs' : 'w-9 h-9 text-sm';
  if (assetImage) {
    if (/^https?:\/\//i.test(assetImage)) {
      return (
        <img
          src={assetImage}
          alt={assetCode}
          className={`${dims} rounded-lg shrink-0 object-cover`}
        />
      );
    }
    return (
      <span className={`${dims} flex items-center justify-center rounded-lg text-lg shrink-0`}>
        {assetImage}
      </span>
    );
  }
  // Per-asset deterministic colour (keyless logo substitute) instead of one flat per-type colour.
  return (
    <span
      className={`flex items-center justify-center ${dims} rounded-lg font-bold shrink-0`}
      style={assetColorStyle(assetCode ?? '')}
    >
      {(assetCode ?? '?').slice(0, 3).toUpperCase()}
    </span>
  );
}
