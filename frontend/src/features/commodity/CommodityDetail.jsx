import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { commodityService } from './services/commodityService';
import { commodityName } from '../../shared/utils/commodityName';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';
import BankRatesSection from '../bankRates/BankRatesSection';

// Commodity → the bank "gold/silver" product whose buy/sell rates the banks publish. Gram gold (XAUTRYG) and gram
// silver (XAGTRYG) both have bank rates; other metals/energy have none, so they simply show no section.
const COMMODITY_BANK_GOLD = { XAUTRYG: 'GRAM_ALTIN', XAGTRYG: 'GUMUS' };

function CommodityHeader({ asset }) {
  const { t } = useTranslation();
  const meta = asset.metadata || {};
  const display = commodityName(t, asset.code, asset.name);
  const subtitle = [asset.code, meta.unit].filter(Boolean).join(' · ');
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-orange-400/10 text-orange-400 text-sm font-bold">
        {(asset.code || '').slice(0, 3).toUpperCase()}
      </span>
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{display}</h1>
        {subtitle && <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{subtitle}</p>}
      </div>
    </>
  );
}

export default function CommodityDetail() {
  const { code } = useParams();
  const { t } = useTranslation();

  return (
    <AssetDetailPage
      assetCode={code}
      assetType="COMMODITY"
      chartAssetType="COMMODITY"
      queryKeyPrefix="commodity"
      fetchAsset={() => commodityService.getByCode(code)}
      fetchHistory={(_, range) => commodityService.getHistory(code, range)}
      backRoute="/commodities"
      excludeCompare={[code]}
      renderHeader={(asset) => <CommodityHeader asset={asset} />}
      renderBelowChart={() => (
        COMMODITY_BANK_GOLD[code]
          ? <BankRatesSection currency={COMMODITY_BANK_GOLD[code]} kind="GOLD" />
          : null
      )}
      showBuyButton={true}
      getBuyProps={(asset) => ({
        assetType: 'COMMODITY',
        assetCode: asset.code || code,
        assetName: commodityName(t, asset.code, asset.name) || code,
        currentPrice: asset.price,
      })}
    />
  );
}
