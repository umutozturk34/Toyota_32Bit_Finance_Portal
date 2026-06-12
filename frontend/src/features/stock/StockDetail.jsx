import { useParams } from 'react-router-dom';
import { stockService } from './services/stockService';
import AssetDetailPage from '../../shared/components/asset/AssetDetailPage';

function StockHeader({ asset }) {
  const displaySymbol = asset.code?.replace('.IS', '') || '';
  return (
    <>
      <span className="flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-success/10 text-success text-sm font-bold">
        {displaySymbol.slice(0, 3).toUpperCase()}
      </span>
      <div className="min-w-0">
        <h1 className="text-xl font-bold text-fg truncate">{displaySymbol}</h1>
        <p className="text-xs text-fg-muted truncate max-w-[12rem] sm:max-w-[18rem]">{asset.name}</p>
      </div>
    </>
  );
}

export default function StockDetail() {
  const { symbol } = useParams();
  const historySym = symbol.endsWith('.IS') ? symbol : `${symbol}.IS`;
  const chartSymbol = symbol.endsWith('.IS') ? symbol.replace('.IS', '') : symbol;

  return (
    <AssetDetailPage
      assetCode={symbol}
      assetType="STOCK"
      chartAssetType="BIST"
      queryKeyPrefix="stock"
      fetchAsset={() => stockService.getByCode(symbol)}
      fetchHistory={(_, range) => stockService.getHistory(historySym, range)}
      backRoute="/stocks"
      excludeCompare={[historySym]}
      renderHeader={(asset) => <StockHeader asset={asset} />}
      showBuyButton={true}
      getBuyProps={(asset) => ({
        assetType: 'STOCK',
        assetCode: asset.code || symbol,
        assetName: asset.name || chartSymbol,
        currentPrice: asset.price,
      })}
    />
  );
}
