import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { unifiedMarketService } from '../services/unifiedMarketService';
import { resolveSynonym, categoryToMacroType } from '../utils/searchSynonyms';

function buildEffective(trimmed, filterType, locale) {
  const synonym = resolveSynonym(trimmed, locale);
  if (!synonym) return { search: trimmed, type: filterType };
  const search = synonym.expandedQuery ?? (synonym.category ? '' : trimmed);
  const macroType = synonym.category ? categoryToMacroType(synonym.category) : null;
  const type = macroType ?? synonym.type ?? filterType;
  return { search, type };
}

function isExcludedType(type, excludeTypes) {
  if (!type || excludeTypes.length === 0) return false;
  for (const ex of excludeTypes) {
    if (ex === type) return true;
    if (ex === 'MACRO' && type.startsWith('MACRO_')) return true;
  }
  return false;
}

export default function useSearchSuggestions({
  query,
  filterType,
  suggestFn,
  excludeCodes = [],
  excludeTypes = [],
  pageSize = 8,
  enabled = true,
  onClose,
}) {
  const { i18n } = useTranslation();
  const [activeIndex, setActiveIndex] = useState(-1);
  const containerRef = useRef(null);

  const trimmed = query?.trim() || '';
  const queryEnabled = enabled && trimmed.length >= 2;

  const [trackedTrimmed, setTrackedTrimmed] = useState(trimmed);
  if (trimmed !== trackedTrimmed) {
    setTrackedTrimmed(trimmed);
    setActiveIndex(-1);
  }

  const effective = buildEffective(trimmed, filterType, i18n.language);

  const { data, isFetching } = useQuery({
    queryKey: ['searchSuggestions', effective.search, effective.type, !!suggestFn],
    queryFn: () => suggestFn
      ? suggestFn(effective.search || trimmed)
      : unifiedMarketService.search({
          search: effective.search,
          ...(effective.type && { type: effective.type }),
          size: pageSize,
        }),
    enabled: queryEnabled,
    staleTime: 15_000,
  });

  const raw = suggestFn ? (data || []) : (data?.content || []);
  const filteredByCode = excludeCodes.length > 0
    ? raw.filter(a => !excludeCodes.includes(a.code))
    : raw;
  const suggestions = excludeTypes.length > 0
    ? filteredByCode.filter(a => !isExcludedType(a.type, excludeTypes))
    : filteredByCode;

  useEffect(() => {
    if (!onClose) return;
    const handler = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) onClose();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onClose]);

  const buildKeyDown = useCallback((onSelect, onEscape) => (e) => {
    if (suggestions.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIndex(i => (i + 1) % suggestions.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIndex(i => (i - 1 + suggestions.length) % suggestions.length);
    } else if (e.key === 'Enter' && activeIndex >= 0) {
      e.preventDefault();
      onSelect(suggestions[activeIndex]);
    } else if (e.key === 'Escape') {
      (onEscape || onClose)?.();
    }
  }, [suggestions, activeIndex, onClose]);

  return {
    containerRef,
    suggestions,
    activeIndex,
    setActiveIndex,
    isFetching,
    buildKeyDown,
  };
}
