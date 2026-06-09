import { LayoutGrid, TrendingUp, Newspaper, Bookmark, Trophy, Medal } from 'lucide-react';
import { randomId } from '../../../shared/utils/id';
import AssetCardsSection from './AssetCardsSection';
import MoversSection from './MoversSection';
import NewsSection from './NewsSection';
import WatchlistSection from './WatchlistSection';
import BeatersSection from './BeatersSection';
import ReturnsSection from './ReturnsSection';

export const SECTION_DEFINITIONS = Object.freeze({
  ASSET_CARDS: {
    labelKey: 'sectionRegistry.ASSET_CARDS.label',
    descriptionKey: 'sectionRegistry.ASSET_CARDS.description',
    Icon: LayoutGrid,
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
  BENCHMARK_BEATERS: {
    labelKey: 'sectionRegistry.BENCHMARK_BEATERS.label',
    descriptionKey: 'sectionRegistry.BENCHMARK_BEATERS.description',
    Icon: Trophy,
    Component: BeatersSection,
    configurable: true,
    multiInstance: true,
  },
  ASSET_RETURNS: {
    labelKey: 'sectionRegistry.ASSET_RETURNS.label',
    descriptionKey: 'sectionRegistry.ASSET_RETURNS.description',
    Icon: Medal,
    Component: ReturnsSection,
    configurable: true,
    multiInstance: true,
  },
});

export function definitionFor(kind) {
  return SECTION_DEFINITIONS[kind] || null;
}

export function newSectionId(kind) {
  const slug = kind.toLowerCase().replace(/_/g, '-');
  return `${slug}-${randomId().slice(0, 6)}`;
}
