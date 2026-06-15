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

// react-icons/gi on purpose (not the app-standard lucide): each precious-metal/commodity gets a distinctive
// glyph — gold bar, coin pile, crown coin, diamond ring, pearl necklace (the bilezik), oil drum — that lucide's
// small flat set can't tell apart (it collapses every gold type to Coins/Gem). Keep gi here for that variety.
// Colour is a SINGLE shared tone on purpose: the old per-type rainbow of ambers/yellows/oranges/rose/slate read
// as visual noise in the strip — the distinct glyph alone carries the identity, the tone stays cohesive.
const ICON_TONE = 'text-warning';
const COMMODITY_VISUAL = {
  GRAM_ALTIN:        { Icon: GiGoldBar,       color: ICON_TONE },
  CEYREK_ALTIN:      { Icon: GiTwoCoins,      color: ICON_TONE },
  YARIM_ALTIN:       { Icon: GiCoinsPile,     color: ICON_TONE },
  TAM_ALTIN:         { Icon: GiGoldNuggets,   color: ICON_TONE },
  BESLI_ALTIN:       { Icon: GiCoinflip,      color: ICON_TONE },
  CUMHURIYET_ALTINI: { Icon: GiCrownCoin,     color: ICON_TONE },
  HAMIT_ALTIN:       { Icon: GiRoyalLove,     color: ICON_TONE },
  RESAT_ALTIN:       { Icon: GiStarMedal,     color: ICON_TONE },
  ATA_ALTIN:         { Icon: GiGoldShell,     color: ICON_TONE },
  GRAM_HAS_ALTIN:    { Icon: GiCutDiamond,    color: ICON_TONE },
  AYAR_14:           { Icon: GiDiamondRing,   color: ICON_TONE },
  AYAR_18:           { Icon: GiDiamonds,      color: ICON_TONE },
  AYAR_22_BILEZIK:   { Icon: GiPearlNecklace, color: ICON_TONE },
  GUMUS:             { Icon: GiSilverBullet,  color: ICON_TONE },
  PETROL:            { Icon: GiOilDrum,       color: ICON_TONE },
  BRENT:             { Icon: GiOilPump,       color: ICON_TONE },
};

const COMMODITY_FALLBACK = { Icon: Coins, color: ICON_TONE };

export function commodityVisual(code) {
  return COMMODITY_VISUAL[code] || COMMODITY_FALLBACK;
}
