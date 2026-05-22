import {
  GiGoldBar,
  GiTwoCoins,
  GiCoinsPile,
  GiCrownCoin,
  GiStarMedal,
  GiRoyalLove,
  GiGoldNuggets,
  GiGoldShell,
  GiCutDiamond,
  GiDiamonds,
  GiDiamondRing,
  GiPearlNecklace,
  GiSilverBullet,
  GiOilDrum,
  GiOilPump,
  GiCoinflip,
} from 'react-icons/gi';
import { Coins } from 'lucide-react';

const COMMODITY_VISUAL = {
  GRAM_ALTIN:        { Icon: GiGoldBar,       color: 'text-amber-300' },
  CEYREK_ALTIN:      { Icon: GiTwoCoins,      color: 'text-amber-400' },
  YARIM_ALTIN:       { Icon: GiCoinsPile,     color: 'text-amber-400' },
  TAM_ALTIN:         { Icon: GiGoldNuggets,   color: 'text-amber-500' },
  BESLI_ALTIN:       { Icon: GiCoinflip,      color: 'text-amber-500' },
  CUMHURIYET_ALTINI: { Icon: GiCrownCoin,     color: 'text-yellow-400' },
  HAMIT_ALTIN:       { Icon: GiRoyalLove,     color: 'text-yellow-500' },
  RESAT_ALTIN:       { Icon: GiStarMedal,     color: 'text-yellow-300' },
  ATA_ALTIN:         { Icon: GiGoldShell,     color: 'text-amber-300' },
  GRAM_HAS_ALTIN:    { Icon: GiCutDiamond,    color: 'text-yellow-200' },
  AYAR_14:           { Icon: GiDiamondRing,   color: 'text-orange-300' },
  AYAR_18:           { Icon: GiDiamonds,      color: 'text-orange-400' },
  AYAR_22_BILEZIK:   { Icon: GiPearlNecklace, color: 'text-rose-300' },
  GUMUS:             { Icon: GiSilverBullet,  color: 'text-slate-300' },
  PETROL:            { Icon: GiOilDrum,       color: 'text-zinc-400' },
  BRENT:             { Icon: GiOilPump,       color: 'text-zinc-500' },
};

const COMMODITY_FALLBACK = { Icon: Coins, color: 'text-warning' };

export function commodityVisual(code) {
  return COMMODITY_VISUAL[code] || COMMODITY_FALLBACK;
}
