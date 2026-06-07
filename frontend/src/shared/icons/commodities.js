import { Coins, Gem, Droplet } from 'lucide-react';

// Consistent lucide line-icons (matching the rest of the app) instead of the old mixed react-icons/gi set,
// which combined two icon styles AND used unrelated glyphs (a heart for HAMIT_ALTIN, a bullet for GUMUS,
// diamonds/ring for pure gold) — that read as cluttered. Now: gold bullion & coins → Coins, karat jewelry →
// Gem, crude oil → Droplet, each item distinguished by colour shade.
const COMMODITY_VISUAL = {
  GRAM_ALTIN:        { Icon: Coins,   color: 'text-amber-300' },
  GRAM_HAS_ALTIN:    { Icon: Coins,   color: 'text-yellow-200' },
  CEYREK_ALTIN:      { Icon: Coins,   color: 'text-amber-400' },
  YARIM_ALTIN:       { Icon: Coins,   color: 'text-amber-500' },
  TAM_ALTIN:         { Icon: Coins,   color: 'text-amber-600' },
  BESLI_ALTIN:       { Icon: Coins,   color: 'text-yellow-500' },
  CUMHURIYET_ALTINI: { Icon: Coins,   color: 'text-yellow-400' },
  HAMIT_ALTIN:       { Icon: Coins,   color: 'text-yellow-600' },
  RESAT_ALTIN:       { Icon: Coins,   color: 'text-yellow-300' },
  ATA_ALTIN:         { Icon: Coins,   color: 'text-amber-500' },
  AYAR_14:           { Icon: Gem,     color: 'text-orange-300' },
  AYAR_18:           { Icon: Gem,     color: 'text-orange-400' },
  AYAR_22_BILEZIK:   { Icon: Gem,     color: 'text-amber-400' },
  GUMUS:             { Icon: Coins,   color: 'text-slate-300' },
  PETROL:            { Icon: Droplet, color: 'text-zinc-400' },
  BRENT:             { Icon: Droplet, color: 'text-zinc-500' },
};

const COMMODITY_FALLBACK = { Icon: Coins, color: 'text-warning' };

export function commodityVisual(code) {
  return COMMODITY_VISUAL[code] || COMMODITY_FALLBACK;
}
