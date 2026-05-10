import { Layers, TrendingUp, Newspaper, Bookmark } from 'lucide-react';
import AssetCardsSection from './AssetCardsSection';
import MoversSection from './MoversSection';
import NewsSection from './NewsSection';
import WatchlistSection from './WatchlistSection';

export const SECTION_DEFINITIONS = Object.freeze({
  ASSET_CARDS: {
    labelKey: 'sectionRegistry.ASSET_CARDS.label',
    descriptionKey: 'sectionRegistry.ASSET_CARDS.description',
    Icon: Layers,
    Component: AssetCardsSection,
    configurable: true,
    multiInstance: true,
  },
  MOVERS: {
    labelKey: 'sectionRegistry.MOVERS.label',
    descriptionKey: 'sectionRegistry.MOVERS.description',
    Icon: TrendingUp,
    Component: MoversSection,
    configurable: false,
    multiInstance: true,
  },
  WATCHLIST: {
    labelKey: 'sectionRegistry.WATCHLIST.label',
    descriptionKey: 'sectionRegistry.WATCHLIST.description',
    Icon: Bookmark,
    Component: WatchlistSection,
    configurable: true,
    multiInstance: true,
  },
  NEWS: {
    labelKey: 'sectionRegistry.NEWS.label',
    descriptionKey: 'sectionRegistry.NEWS.description',
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
