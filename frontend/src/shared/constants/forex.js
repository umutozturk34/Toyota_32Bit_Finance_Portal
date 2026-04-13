const FOREX_FLAGS = {
  'USDTRY': '🇺🇸',
  'EURTRY': '🇪🇺',
  'GBPTRY': '🇬🇧',
  'JPYTRY': '🇯🇵',
  'CHFTRY': '🇨🇭',
  'CADTRY': '🇨🇦',
  'AUDTRY': '🇦🇺',
  'SARTRY': '🇸🇦',
  'KRWTRY': '🇰🇷',
  'SEKTRY': '🇸🇪',
  'NOKTRY': '🇳🇴',
  'DKKTRY': '🇩🇰',
  'KWDTRY': '🇰🇼',
  'RONTRY': '🇷🇴',
  'RUBTRY': '🇷🇺',
  'CNYTRY': '🇨🇳',
  'PKRTRY': '🇵🇰',
  'QARTRY': '🇶🇦',
  'AZNTRY': '🇦🇿',
  'AEDTRY': '🇦🇪',
  'KZTTRY': '🇰🇿',
};

export const getForexFlag = (currencyCode) => FOREX_FLAGS[currencyCode] || '💱';

export const getBaseCurrency = (currencyCode) => currencyCode.replace('TRY', '');
