const COMMODITY_DISPLAY_CODES = {
    'GC=F': 'XAUTRY',
    'SI=F': 'XAGTRY',
    'PL=F': 'XPTTRY',
    'PA=F': 'XPDTRY',
    'BZ=F': 'BRENT',
    'ZW=F': 'WHEAT',
    'HG=F': 'COPPER',
};

export const commodityDisplayCode = (code) => COMMODITY_DISPLAY_CODES[code] || code;

export const isCommodityIndex = (code) => code?.startsWith('GOLD_') || code?.startsWith('SILVER_');

export const commodityLabel = (asset) => {
    if (asset?.type !== 'COMMODITY') {
        return asset?.name || (asset?.code || '').replace('.IS', '');
    }
    return asset.name || commodityDisplayCode(asset.code);
};

export const commoditySecondaryCode = (code) => commodityDisplayCode(code);

export const assetCodeLabel = (assetType, code) => {
    if (assetType === 'COMMODITY') return commodityDisplayCode(code);
    return (code || '').replace('.IS', '');
};
