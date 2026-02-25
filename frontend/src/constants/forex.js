const FOREX_METADATA = {
  'USDTRY': { name: 'ABD Doları', icon: '🇺🇸', flag: '🇺🇸' },
  'EURTRY': { name: 'Euro', icon: '🇪🇺', flag: '🇪🇺' },
  'GBPTRY': { name: 'İngiliz Sterlini', icon: '🇬🇧', flag: '🇬🇧' },
  'JPYTRY': { name: 'Japon Yeni', icon: '🇯🇵', flag: '🇯🇵' },
  'CHFTRY': { name: 'İsviçre Frangı', icon: '🇨🇭', flag: '🇨🇭' },
  'CADTRY': { name: 'Kanada Doları', icon: '🇨🇦', flag: '🇨🇦' },
  'AUDTRY': { name: 'Avustralya Doları', icon: '🇦🇺', flag: '🇦🇺' },
  'SARTRY': { name: 'Suudi Riyali', icon: '🇸🇦', flag: '🇸🇦' },
  'KRWTRY': { name: 'Güney Kore Wonu', icon: '🇰🇷', flag: '🇰🇷' },
  'SEKTRY': { name: 'İsveç Kronu', icon: '🇸🇪', flag: '🇸🇪' },
  'NOKTRY': { name: 'Norveç Kronu', icon: '🇳🇴', flag: '🇳🇴' },
  'DKKTRY': { name: 'Danimarka Kronu', icon: '🇩🇰', flag: '🇩🇰' },
  'KWDTRY': { name: 'Kuveyt Dinarı', icon: '🇰🇼', flag: '🇰🇼' },
  'RONTRY': { name: 'Rumen Leyi', icon: '🇷🇴', flag: '🇷🇴' },
  'RUBTRY': { name: 'Rus Rublesi', icon: '🇷🇺', flag: '🇷🇺' },
  'CNYTRY': { name: 'Çin Yuanı', icon: '🇨🇳', flag: '🇨🇳' },
  'PKRTRY': { name: 'Pakistan Rupisi', icon: '🇵🇰', flag: '🇵🇰' },
  'QARTRY': { name: 'Katar Riyali', icon: '🇶🇦', flag: '🇶🇦' },
  'AZNTRY': { name: 'Azerbaycan Manatı', icon: '🇦🇿', flag: '🇦🇿' },
  'AEDTRY': { name: 'BAE Dirhemi', icon: '🇦🇪', flag: '🇦🇪' },
  'KZTTRY': { name: 'Kazakistan Tengesi', icon: '🇰🇿', flag: '🇰🇿' }
};
export const getForexPairs = () => {
  return Object.keys(FOREX_METADATA);
};
export const getForexMetadata = (currencyCode) => {
  return FOREX_METADATA[currencyCode] || {
    name: currencyCode,
    icon: '💱',
    flag: '💱'
  };
};
export const getForexDisplayName = (currencyCode) => {
  const metadata = getForexMetadata(currencyCode);
  return metadata.name;
};
export const getForexIcon = (currencyCode) => {
  const metadata = getForexMetadata(currencyCode);
  return metadata.icon;
};
export const getForexFlag = (currencyCode) => {
  const metadata = getForexMetadata(currencyCode);
  return metadata.flag;
};
export const getBaseCurrency = (currencyCode) => {
  return currencyCode.replace('TRY', '');
};
export default {
  getForexPairs,
  getForexMetadata,
  getForexDisplayName,
  getForexIcon,
  getForexFlag,
  getBaseCurrency
};
