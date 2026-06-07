import { Hash, DollarSign, BarChart3, Wallet, Calendar } from 'lucide-react';
import { currentLocaleTag } from '../../../shared/utils/formatters';

export const formatEntryDate = (v) => v ? new Date(v).toLocaleDateString(currentLocaleTag(), { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

const TYPE_ROUTES = { STOCK: '/stocks', CRYPTO: '/crypto', FOREX: '/forex', FUND: '/funds', COMMODITY: '/commodities', VIOP: '/viop' };
export const marketHref = (type, code) => `${TYPE_ROUTES[type] ?? '/market'}/${encodeURIComponent(code)}`;

export const STAT_CARD_DEFS = [
  { key: 'quantity', labelKey: 'quantity', Icon: Hash, format: (v) => Number(v).toLocaleString(currentLocaleTag(), { maximumFractionDigits: 6 }) },
  { key: 'entryDate', labelKey: 'entryDate', Icon: Calendar, format: formatEntryDate },
  { key: 'entryPrice', labelKey: 'entryPrice', Icon: DollarSign, money: true },
  { key: 'currentPriceTry', labelKey: 'currentPrice', Icon: BarChart3, money: true },
  { key: 'marketValueTry', labelKey: 'marketValue', Icon: Wallet, money: true },
];

// A VİOP lot's direction: structured field first, then the legacy "LONG · …" / "SHORT · …" assetName prefix.
// The prefix is only honoured when it is exactly LONG/SHORT — a plain assetName (no " · ") must yield null,
// not the whole string (which would be misread as a direction and force headDirectionSign to +1 for a SHORT).
export const lotDirection = (lot) => {
  if (lot?.derivative?.direction) return lot.derivative.direction;
  const prefix = String(lot?.assetName || '').split(' · ')[0];
  return (prefix === 'LONG' || prefix === 'SHORT') ? prefix : null;
};
