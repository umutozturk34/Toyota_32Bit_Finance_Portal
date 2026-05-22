import { ASSET_TYPE_STYLES } from '../../../shared/constants/assetTypes';
import { assetCodeLabel } from '../../../shared/utils/assetCode';

export default function PositionAssetBadge({ pos }) {
  const typeStyle = ASSET_TYPE_STYLES[pos.assetType] || ASSET_TYPE_STYLES.CRYPTO;
  if (pos.assetImage) {
    return /^https?:\/\//i.test(pos.assetImage)
      ? <img src={pos.assetImage} alt={pos.assetCode} className="w-8 h-8 rounded-lg shrink-0" />
      : <span className="flex items-center justify-center w-8 h-8 rounded-lg text-xl shrink-0">{pos.assetImage}</span>;
  }
  return (
    <span className={`flex items-center justify-center w-8 h-8 rounded-lg ${typeStyle.bg} text-sm font-bold ${typeStyle.text} shrink-0`}>
      {assetCodeLabel(pos.assetType, pos.assetCode).slice(0, 3).toUpperCase()}
    </span>
  );
}
