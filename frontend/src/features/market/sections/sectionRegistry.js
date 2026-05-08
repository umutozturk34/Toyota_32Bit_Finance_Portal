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
    configurable: true,
    multiInstance: true,
  },
  MOVERS: {
    label: 'Yükselen / Düşen',
    description: 'Seçtiğin piyasanın günlük hareketleri',
    Icon: TrendingUp,
    Component: MoversSection,
    configurable: false,
    multiInstance: true,
  },
  WATCHLIST: {
    label: 'Takip Listesi',
    description: 'Maks 5 takip varlığı gösterir',
    Icon: Bookmark,
    Component: WatchlistSection,
    configurable: true,
    multiInstance: true,
  },
  NEWS: {
    label: 'Haberler',
    description: 'Kategori bazlı son haberler',
    Icon: Newspaper,
    Component: NewsSection,
    configurable: true,
    multiInstance: true,
  },
});

export function definitionFor(kind) {
  return SECTION_DEFINITIONS[kind] || null;
}

export function newSectionId(kind) {
  const slug = kind.toLowerCase().replace(/_/g, '-');
  return `${slug}-${Math.random().toString(36).slice(2, 8)}`;
}
