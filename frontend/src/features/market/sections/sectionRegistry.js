import { Layers, TrendingUp, Newspaper, Bookmark } from 'lucide-react';
import AssetCardsSection from './AssetCardsSection';
import MoversSection from './MoversSection';
import NewsSection from './NewsSection';
import WatchlistSection from './WatchlistSection';

export const SECTION_DEFINITIONS = Object.freeze({
  ASSET_CARDS: {
    label: 'Asset Kartları',
    description: 'Sabitlediğin varlıkların anlık fiyat panosu',
    Icon: Layers,
    Component: AssetCardsSection,
    slot: 'strip',
    configurable: true,
    multiInstance: true,
  },
  MOVERS: {
    label: 'Yükselen / Düşen',
    description: 'Seçtiğin piyasanın günlük hareketleri',
    Icon: TrendingUp,
    Component: MoversSection,
    slot: 'main',
    configurable: false,
    multiInstance: false,
  },
  WATCHLIST: {
    label: 'Takip Listesi',
    description: 'Listelerinden birini panelde göster',
    Icon: Bookmark,
    Component: WatchlistSection,
    slot: 'main',
    configurable: true,
    multiInstance: false,
  },
  NEWS: {
    label: 'Haberler',
    description: 'Kategori bazlı son haberler',
    Icon: Newspaper,
    Component: NewsSection,
    slot: 'news',
    configurable: true,
    multiInstance: false,
  },
});

export function definitionFor(kind) {
  return SECTION_DEFINITIONS[kind] || null;
}

export function newSectionId(kind) {
  if (kind === 'ASSET_CARDS') {
    return `asset-cards-${Math.random().toString(36).slice(2, 8)}`;
  }
  return kind.toLowerCase();
}
